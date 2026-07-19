package dev.marginalis.plugin.transport

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import dev.marginalis.core.AnchorPolicy
import dev.marginalis.core.Author
import dev.marginalis.core.CommentThread
import dev.marginalis.core.Message
import dev.marginalis.core.ThreadStatus
import dev.marginalis.plugin.store.Authors
import dev.marginalis.plugin.store.MarginalisStore
import dev.marginalis.plugin.ui.ThreadGutterIconRenderer
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.ide.RestService
import java.time.format.DateTimeFormatter

/**
 * The agent's transport: JSON endpoints on the IDE's built-in HTTP server
 * (port 63342 by default; each running IDE process has its own server one
 * port up).
 *
 * Endpoints:
 *   GET  /api/marginalis/ping             -> {status, ide, projects}
 *   POST /api/marginalis/comment_add      {file, line, body, anchor_text?, order?, tour?}
 *   POST /api/marginalis/comment_reply    {thread_id, body}
 *   POST /api/marginalis/comment_resolve  {thread_id}
 *   POST /api/marginalis/comment_reopen   {thread_id}
 *   POST /api/marginalis/comment_resolve_all  {file?}
 *   POST /api/marginalis/comment_clear_all    {file?}
 *   GET  /api/marginalis/comment_list?file=&status=&unread_only=
 *
 * `file` is project-relative; `line` is 1-based, matching how agents read
 * files. Listing marks messages seen and says so explicitly (per-message
 * `newly_seen`, top-level `marked_seen`) — that read receipt is what lets
 * the user trust "the agent will see this on its next turn".
 *
 * Handlers run on a background HTTP thread; editor markup is marshalled to
 * the EDT, document/VFS reads take read actions.
 */
class MarginalisRestService : RestService() {

    override fun getServiceName(): String = "marginalis"

    override fun isMethodSupported(method: HttpMethod): Boolean =
        method === HttpMethod.GET || method === HttpMethod.POST

    // Two cooperating local participants over loopback: skip the built-in
    // server's origin-confirmation dialog, which would block headless calls.
    override fun isHostTrusted(request: FullHttpRequest, urlDecoder: QueryStringDecoder): Boolean = true

    // Default is 30/min — an agent annotating a file in one turn bursts past that.
    override fun getMaxRequestsPerMinute(): Int = 1000

    override fun execute(
        urlDecoder: QueryStringDecoder,
        request: FullHttpRequest,
        context: ChannelHandlerContext,
    ): String? {
        val endpoint = urlDecoder.path().removePrefix("/api/${getServiceName()}").trim('/')
        when (endpoint) {
            "ping" -> sendJson(pingInfo(), request, context)
            "comment_add" -> post(request, context) { handleCommentAdd(it, request, context) }
            "comment_reply" -> post(request, context) { handleCommentReply(it, request, context) }
            "comment_resolve" -> post(request, context) { handleStatusChange(it, request, context, resolve = true) }
            "comment_reopen" -> post(request, context) { handleStatusChange(it, request, context, resolve = false) }
            "comment_resolve_all" -> post(request, context) { handleResolveAll(it, request, context) }
            "comment_clear_all" -> post(request, context) { handleClearAll(it, request, context) }
            "comment_list" -> handleCommentList(urlDecoder, request, context)
            else -> sendError(HttpResponseStatus.NOT_FOUND, "unknown endpoint '$endpoint'", request, context)
        }
        return null
    }

    /**
     * Self-describing ping: with several IDE processes running, an agent
     * must be able to ask "which projects do YOU have open?" to find the
     * right server instead of inferring from 404s.
     */
    private fun pingInfo(): JsonObject = JsonObject().apply {
        addProperty("status", "ok")
        val appInfo = ApplicationInfo.getInstance()
        addProperty("ide", "${appInfo.versionName} ${appInfo.fullVersion}")
        add(
            "projects",
            JsonArray().apply {
                ApplicationManager.getApplication().runReadAction {
                    for (project in ProjectManager.getInstance().openProjects) {
                        if (project.isDisposed) continue
                        add(
                            JsonObject().apply {
                                addProperty("name", project.name)
                                addProperty("path", project.guessProjectDir()?.path ?: project.basePath)
                            },
                        )
                    }
                }
            },
        )
    }

    /** Shared POST plumbing: method check + JSON body parse. */
    private fun post(request: FullHttpRequest, context: ChannelHandlerContext, handler: (JsonObject) -> Unit) {
        if (request.method() !== HttpMethod.POST) {
            return sendError(HttpResponseStatus.METHOD_NOT_ALLOWED, "this endpoint requires POST", request, context)
        }
        val json = try {
            JsonParser.parseString(request.content().toString(Charsets.UTF_8)).asJsonObject
        } catch (e: Exception) {
            return sendError(HttpResponseStatus.BAD_REQUEST, "body must be a JSON object", request, context)
        }
        handler(json)
    }

    // ---------------------------------------------------------------- add

    private fun handleCommentAdd(json: JsonObject, request: FullHttpRequest, context: ChannelHandlerContext) {
        val file = json.stringOrNull("file")
            ?: return sendError(HttpResponseStatus.BAD_REQUEST, "missing 'file' (project-relative path)", request, context)
        val line1 = json.intOrNull("line")
            ?: return sendError(HttpResponseStatus.BAD_REQUEST, "missing or non-integer 'line' (1-based)", request, context)
        val body = json.stringOrNull("body")
            ?: return sendError(HttpResponseStatus.BAD_REQUEST, "missing 'body'", request, context)
        val anchorText = json.stringOrNull("anchor_text")
        val order = json.intOrNull("order")
        val tourLabel = json.stringOrNull("tour")

        val (project, vFile) = ApplicationManager.getApplication()
            .runReadAction(Computable { resolveFile(file) })
            ?: return sendError(
                HttpResponseStatus.NOT_FOUND,
                "'$file' not found in any open project (paths are project-relative)",
                request, context,
            )

        var error: String? = null
        var errorStatus = HttpResponseStatus.BAD_REQUEST
        var thread: CommentThread? = null
        var adjusted = false

        ApplicationManager.getApplication().invokeAndWait {
            val document = FileDocumentManager.getInstance().getDocument(vFile)
            if (document == null) {
                error = "'$file' has no text document (binary or too large?)"
                return@invokeAndWait
            }
            var line0 = line1 - 1
            if (line0 < 0 || line0 >= document.lineCount) {
                error = "line $line1 out of range: '$file' has ${document.lineCount} lines. Re-read the file."
                errorStatus = HttpResponseStatus.CONFLICT
                return@invokeAndWait
            }

            // The agent's line numbers may be stale; anchor text is the truth.
            if (anchorText != null && !AnchorPolicy.lineMatches(lineText(document, line0), anchorText)) {
                val found = AnchorPolicy.findAnchorLine(
                    lineCount = document.lineCount,
                    lineTextAt = { lineText(document, it) },
                    nearLine = line0,
                    anchorText = anchorText,
                )
                if (found == null) {
                    error = "anchor_text does not match line $line1 or the ±${AnchorPolicy.SEARCH_WINDOW} lines " +
                        "around it. The file has probably changed — re-read it."
                    errorStatus = HttpResponseStatus.CONFLICT
                    return@invokeAndWait
                }
                line0 = found
                adjusted = true
            }

            val created = CommentThread(file, line0, lineText(document, line0), order = order, tour = tourLabel)
            created.addMessage(Message(Authors.agent, body))
            val store = MarginalisStore.getInstance(project)
            val markup = DocumentMarkupModel.forDocument(document, project, true)
            val highlighter = markup.addLineHighlighter(line0, HighlighterLayer.LAST, null)
            highlighter.gutterIconRenderer = ThreadGutterIconRenderer(project, created)
            store.setMarker(created, highlighter)
            store.threads.add(created)
            thread = created
        }

        val added = thread ?: return sendError(errorStatus, error ?: "internal error", request, context)
        sendJson(
            JsonObject().apply {
                addProperty("thread_id", added.id)
                addProperty("file", added.file)
                addProperty("line", added.line + 1)
                addProperty("line_adjusted", adjusted)
                addProperty("status", added.status.kind.name.lowercase())
            },
            request, context,
        )
    }

    private fun lineText(document: Document, line: Int): String =
        document.getText(TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)))

    // -------------------------------------------------------------- reply

    private fun handleCommentReply(json: JsonObject, request: FullHttpRequest, context: ChannelHandlerContext) {
        val (project, thread) = lookupThread(json, request, context) ?: return
        val body = json.stringOrNull("body")
            ?: return sendError(HttpResponseStatus.BAD_REQUEST, "missing 'body'", request, context)
        val message = Message(Authors.agent, body)
        thread.addMessage(message)
        MarginalisStore.getInstance(project).threads.notifyChanged(thread)
        sendJson(
            JsonObject().apply {
                addProperty("message_id", message.id)
                addProperty("thread_id", thread.id)
                addProperty("status", thread.status.kind.name.lowercase())
            },
            request, context,
        )
    }

    // ---------------------------------------------------- resolve / reopen

    private fun handleStatusChange(
        json: JsonObject,
        request: FullHttpRequest,
        context: ChannelHandlerContext,
        resolve: Boolean,
    ) {
        val (project, thread) = lookupThread(json, request, context) ?: return
        if (resolve) thread.resolve(Authors.agent) else thread.reopen()
        MarginalisStore.getInstance(project).threads.notifyChanged(thread)
        sendJson(
            JsonObject().apply {
                addProperty("thread_id", thread.id)
                addProperty("status", thread.status.kind.name.lowercase())
            },
            request, context,
        )
    }

    /** Bulk resolve, optionally scoped to one file: {"file"?}. */
    private fun handleResolveAll(json: JsonObject, request: FullHttpRequest, context: ChannelHandlerContext) {
        val fileFilter = json.stringOrNull("file")
        var resolved = 0
        for (project in ProjectManager.getInstance().openProjects) {
            if (project.isDisposed) continue
            val store = MarginalisStore.getInstance(project)
            for (thread in store.threads.all()) {
                if (fileFilter != null && thread.file != fileFilter) continue
                if (thread.status is ThreadStatus.Resolved) continue
                thread.resolve(Authors.agent)
                store.threads.notifyChanged(thread)
                resolved++
            }
        }
        sendJson(JsonObject().apply { addProperty("resolved", resolved) }, request, context)
    }

    /** Bulk delete (threads AND the resolved log), optionally scoped: {"file"?}. */
    private fun handleClearAll(json: JsonObject, request: FullHttpRequest, context: ChannelHandlerContext) {
        val fileFilter = json.stringOrNull("file")
        var cleared = 0
        for (project in ProjectManager.getInstance().openProjects) {
            if (project.isDisposed) continue
            val store = MarginalisStore.getInstance(project)
            if (fileFilter == null) {
                cleared += store.threads.clear().size
            } else {
                for (thread in store.threads.all().filter { it.file == fileFilter }) {
                    store.threads.remove(thread.id)
                    cleared++
                }
            }
        }
        sendJson(JsonObject().apply { addProperty("cleared", cleared) }, request, context)
    }

    private fun lookupThread(
        json: JsonObject,
        request: FullHttpRequest,
        context: ChannelHandlerContext,
    ): Pair<Project, CommentThread>? {
        val threadId = json.stringOrNull("thread_id")
        if (threadId == null) {
            sendError(HttpResponseStatus.BAD_REQUEST, "missing 'thread_id'", request, context)
            return null
        }
        for (project in ProjectManager.getInstance().openProjects) {
            if (project.isDisposed) continue
            MarginalisStore.getInstance(project).threads.byId(threadId)?.let { return project to it }
        }
        sendError(HttpResponseStatus.NOT_FOUND, "no thread with id '$threadId'", request, context)
        return null
    }

    // --------------------------------------------------------------- list

    private fun handleCommentList(
        urlDecoder: QueryStringDecoder,
        request: FullHttpRequest,
        context: ChannelHandlerContext,
    ) {
        val params = urlDecoder.parameters()
        val fileFilter = params["file"]?.firstOrNull()
        val statusFilter = params["status"]?.firstOrNull()?.let {
            try {
                ThreadStatus.Kind.valueOf(it.uppercase())
            } catch (e: IllegalArgumentException) {
                return sendError(HttpResponseStatus.BAD_REQUEST, "invalid status '$it' (open|resolved|orphaned)", request, context)
            }
        }
        val unreadOnly = params["unread_only"]?.firstOrNull()?.toBoolean() ?: false

        val threadsJson = JsonArray()
        var markedSeen = 0
        val timeFormat = DateTimeFormatter.ISO_INSTANT

        ApplicationManager.getApplication().runReadAction {
            for (project in ProjectManager.getInstance().openProjects) {
                if (project.isDisposed) continue
                val store = MarginalisStore.getInstance(project)
                for (thread in store.threads.query(fileFilter, statusFilter, unreadOnly)) {
                    val messagesJson = JsonArray()
                    for (message in thread.messages) {
                        val newlySeen = !message.seenByAgent
                        if (newlySeen) {
                            message.seenByAgent = true
                            markedSeen++
                        }
                        messagesJson.add(
                            JsonObject().apply {
                                addProperty("message_id", message.id)
                                add("author", authorJson(message.author))
                                addProperty("body", message.body)
                                addProperty("created_at", timeFormat.format(message.createdAt))
                                if (newlySeen) addProperty("newly_seen", true)
                            },
                        )
                    }
                    threadsJson.add(
                        JsonObject().apply {
                            addProperty("thread_id", thread.id)
                            addProperty("file", thread.file)
                            addProperty("line", store.currentLine(thread) + 1)
                            addProperty("status", thread.status.kind.name.lowercase())
                            addProperty("created_at", timeFormat.format(thread.createdAt))
                            thread.order?.let { addProperty("order", it) }
                            thread.tour?.let { addProperty("tour", it) }
                            thread.resolvedBy?.let { addProperty("resolved_by", it.displayName) }
                            add("messages", messagesJson)
                        },
                    )
                }
            }
        }

        sendJson(
            JsonObject().apply {
                add("threads", threadsJson)
                addProperty("marked_seen", markedSeen)
            },
            request, context,
        )
    }

    private fun authorJson(author: Author): JsonObject = JsonObject().apply {
        addProperty("kind", if (author is Author.Agent) "agent" else "user")
        addProperty("name", author.displayName)
        (author as? Author.Agent)?.id?.let { addProperty("id", it) }
    }

    // ------------------------------------------------------------ helpers

    /** Resolve a project-relative path against every open project; first match wins. */
    private fun resolveFile(relPath: String): Pair<Project, VirtualFile>? {
        for (project in ProjectManager.getInstance().openProjects) {
            if (project.isDisposed) continue
            val base = project.guessProjectDir() ?: continue
            val vFile = base.findFileByRelativePath(relPath) ?: continue
            return project to vFile
        }
        return null
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.intOrNull(key: String): Int? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt

    private fun sendJson(json: JsonObject, request: FullHttpRequest, context: ChannelHandlerContext) {
        send(json, HttpResponseStatus.OK, request, context)
    }

    private fun sendError(
        status: HttpResponseStatus,
        message: String,
        request: FullHttpRequest,
        context: ChannelHandlerContext,
    ) {
        send(JsonObject().apply { addProperty("error", message) }, status, request, context)
    }

    private fun send(json: JsonObject, status: HttpResponseStatus, request: FullHttpRequest, context: ChannelHandlerContext) {
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(bytes))
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8")
        sendResponse(request, context, response)
    }
}
