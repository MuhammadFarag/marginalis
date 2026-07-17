package dev.marginalis.plugin.transport

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import dev.marginalis.plugin.store.MarginNote
import dev.marginalis.plugin.store.MarginalisStore
import dev.marginalis.plugin.ui.MarginNoteGutterIconRenderer
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

/**
 * M0 transport: Option B of the handover (§6) — the IDE's built-in HTTP server.
 *
 * Endpoints (on the built-in server port, normally 63342):
 *   GET  /api/marginalis/ping          -> {"status":"ok"}
 *   POST /api/marginalis/comment_add   {"file","line","body"} -> {"thread_id",...}
 *   GET  /api/marginalis/comment_list  -> {"notes":[...]}
 *
 * `file` is project-relative; `line` is 1-based (as agents read files).
 *
 * Handlers run on a background HTTP thread (handover §3.5): all editor markup
 * work is marshalled to the EDT via invokeAndWait.
 */
class MarginalisRestService : RestService() {

    override fun getServiceName(): String = "marginalis"

    override fun isMethodSupported(method: HttpMethod): Boolean =
        method === HttpMethod.GET || method === HttpMethod.POST

    // Two cooperating local participants, loopback only (handover §1.4). Skips
    // the built-in server's origin-confirmation dialog, which would otherwise
    // block headless agent calls.
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
            "ping" -> sendJson(JsonObject().apply { addProperty("status", "ok") }, request, context)
            "comment_add" -> handleCommentAdd(request, context)
            "comment_list" -> handleCommentList(request, context)
            else -> sendError(HttpResponseStatus.NOT_FOUND, "unknown endpoint '$endpoint'", request, context)
        }
        return null
    }

    private fun handleCommentAdd(request: FullHttpRequest, context: ChannelHandlerContext) {
        if (request.method() !== HttpMethod.POST) {
            return sendError(HttpResponseStatus.METHOD_NOT_ALLOWED, "comment_add requires POST", request, context)
        }
        val json = try {
            JsonParser.parseString(request.content().toString(Charsets.UTF_8)).asJsonObject
        } catch (e: Exception) {
            return sendError(HttpResponseStatus.BAD_REQUEST, "body must be a JSON object", request, context)
        }
        val file = json.stringOrNull("file")
            ?: return sendError(HttpResponseStatus.BAD_REQUEST, "missing 'file' (project-relative path)", request, context)
        val line1 = try {
            json.get("line")?.takeIf { it.isJsonPrimitive }?.asInt
        } catch (e: JsonSyntaxException) {
            null
        } ?: return sendError(HttpResponseStatus.BAD_REQUEST, "missing or non-integer 'line' (1-based)", request, context)
        val body = json.stringOrNull("body")
            ?: return sendError(HttpResponseStatus.BAD_REQUEST, "missing 'body'", request, context)

        // Computable form: not deprecated, and available all the way back to 252
        // (unlike runReadActionBlocking, which is 2026.1+).
        val (project, vFile) = ApplicationManager.getApplication()
            .runReadAction(Computable { resolveFile(file) })
            ?: return sendError(
                HttpResponseStatus.NOT_FOUND,
                "'$file' not found in any open project (paths are project-relative)",
                request, context,
            )

        var error: String? = null
        var errorStatus = HttpResponseStatus.BAD_REQUEST
        var note: MarginNote? = null

        ApplicationManager.getApplication().invokeAndWait {
            val document = FileDocumentManager.getInstance().getDocument(vFile)
            if (document == null) {
                error = "'$file' has no text document (binary or too large?)"
                return@invokeAndWait
            }
            val line0 = line1 - 1
            if (line0 < 0 || line0 >= document.lineCount) {
                error = "line $line1 out of range: '$file' has ${document.lineCount} lines. Re-read the file."
                errorStatus = HttpResponseStatus.CONFLICT
                return@invokeAndWait
            }
            val created = MarginNote(file, line0, body)
            val markup = DocumentMarkupModel.forDocument(document, project, true)
            val highlighter = markup.addLineHighlighter(line0, HighlighterLayer.LAST, null)
            highlighter.gutterIconRenderer = MarginNoteGutterIconRenderer(created)
            created.highlighter = highlighter
            MarginalisStore.getInstance(project).add(created)
            note = created
        }

        val added = note ?: return sendError(errorStatus, error ?: "internal error", request, context)
        sendJson(
            JsonObject().apply {
                addProperty("thread_id", added.id)
                addProperty("file", added.file)
                addProperty("line", added.line + 1)
                addProperty("anchored", true)
            },
            request, context,
        )
    }

    private fun handleCommentList(request: FullHttpRequest, context: ChannelHandlerContext) {
        val notes = JsonArray()
        for (project in ProjectManager.getInstance().openProjects) {
            if (project.isDisposed) continue
            for (note in MarginalisStore.getInstance(project).all()) {
                notes.add(
                    JsonObject().apply {
                        addProperty("thread_id", note.id)
                        addProperty("file", note.file)
                        // Report the *live* anchor line if the marker moved with edits.
                        val liveLine = note.highlighter
                            ?.takeIf { it.isValid }
                            ?.let { it.document.getLineNumber(it.startOffset) }
                        addProperty("line", (liveLine ?: note.line) + 1)
                        addProperty("body", note.body)
                        addProperty("created_at", note.createdAt.toString())
                    },
                )
            }
        }
        sendJson(JsonObject().apply { add("notes", notes) }, request, context)
    }

    /** Resolve a project-relative path against every open project; first match wins (handover §3.6 keeps this soft for v1). */
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
