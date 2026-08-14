package dev.marginalis.plugin.transport

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import dev.marginalis.core.AnchorPolicy
import dev.marginalis.core.Author
import dev.marginalis.core.CommentThread
import dev.marginalis.core.Intent
import dev.marginalis.core.Message
import dev.marginalis.core.Severity
import dev.marginalis.core.ThreadOrder
import dev.marginalis.core.ThreadStatus
import dev.marginalis.plugin.settings.MarginalisSettings
import dev.marginalis.plugin.store.Authors
import dev.marginalis.plugin.store.MarginalisStore
import dev.marginalis.plugin.ui.MarginalisMarkers
import dev.marginalis.plugin.ui.MarkdownRenderer
import dev.marginalis.plugin.ui.WalkthroughNavigator
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
import java.nio.file.Path
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Properties

/**
 * The agent's transport: JSON endpoints on the IDE's built-in HTTP server
 * (port 63342 by default; each running IDE process has its own server one
 * port up).
 *
 * Endpoints:
 *   GET  /api/marginalis/ping             -> {status, ide, version, projects}
 *   GET  /api/marginalis/agent_guide      -> the agent contract, as markdown
 *   POST /api/marginalis/comment_add      {body, file?, line?, anchor_text?, order?, walkthrough?, severity?, intent?, project?}
 *   POST /api/marginalis/comment_add_batch {items: [comment_add payloads], author_name?, author_id?, project?}
 *   POST /api/marginalis/comment_reply    {thread_id, body}
 *   POST /api/marginalis/comment_resolve  {thread_id}
 *   POST /api/marginalis/comment_reopen   {thread_id}
 *   POST /api/marginalis/comment_resolve_all  {file?}
 *   POST /api/marginalis/comment_reanchor {thread_id, line, anchor_text?}
 *   POST /api/marginalis/comment_reanchor_all {file, project?}
 *   POST /api/marginalis/comment_clear_all    {file?}
 *   GET  /api/marginalis/comment_list?file=&status=&intent=&unread_only=&updated_after=&project=&author_name=&author_id=
 *   POST /api/marginalis/navigate         {file, line?, anchor_text?, project?}
 *
 * Writing/resolving endpoints (comment_add, comment_reply, comment_resolve,
 * comment_resolve_all) also accept `author_name?`/`author_id?` — an agent's
 * self-introduction; without it the author displays as "Agent".
 *
 * `file` is project-relative; `line` is 1-based, matching how agents read
 * files. Omissions widen the subject: no `line` addresses the file as a
 * whole (a file-level thread on comment_add, the file's top on navigate),
 * and on comment_add no `file` either addresses the project itself — which
 * is also the one call that cannot resolve its project from a path, so it
 * takes `project` whenever more than one is open. No response claims what
 * its thread lacks. Listing marks messages seen and says so
 * explicitly (per-message `newly_seen`, top-level `marked_seen`) — that read
 * receipt is what lets the user trust "the agent will see this on its next
 * turn".
 *
 * Handlers run on a background HTTP thread; editor markup is marshalled to
 * the EDT, document/VFS reads take read actions.
 */
class MarginalisRestService : RestService() {

    private val pluginVersion: String? by lazy {
        javaClass.classLoader.getResourceAsStream("marginalis/plugin-version.properties")?.use { stream ->
            Properties().apply { load(stream) }.getProperty("version")?.takeIf { it.isNotBlank() }
        }
    }

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
            "agent_guide" -> sendAgentGuide(request, context)
            "comment_add" -> post(request, context) { handleCommentAdd(it, request, context) }
            "comment_add_batch" -> post(request, context) { handleCommentAddBatch(it, request, context) }
            "comment_reply" -> post(request, context) { handleCommentReply(it, request, context) }
            "comment_resolve" -> post(request, context) { handleStatusChange(it, request, context, resolve = true) }
            "comment_reopen" -> post(request, context) { handleStatusChange(it, request, context, resolve = false) }
            "comment_resolve_all" -> post(request, context) { handleResolveAll(it, request, context) }
            "comment_reanchor" -> post(request, context) { handleReanchor(it, request, context) }
            "comment_reanchor_all" -> post(request, context) { handleReanchorAll(it, request, context) }
            "comment_clear_all" -> post(request, context) { handleClearAll(it, request, context) }
            "comment_list" -> handleCommentList(urlDecoder, request, context)
            "navigate" -> post(request, context) { handleNavigate(it, request, context) }
            else -> sendError(HttpResponseStatus.NOT_FOUND, "unknown endpoint '$endpoint'", request, context)
        }
        return null
    }

    /**
     * The agent contract, served by the build that implements it — the
     * document can't drift from the server because they ship in the same
     * zip (CI checks it mentions every endpoint). Raw markdown, so plain
     * curl is a complete client; the doorbell an agent needs is one line:
     * "ping Marginalis, GET agent_guide, take it from there."
     */
    private fun sendAgentGuide(request: FullHttpRequest, context: ChannelHandlerContext) {
        val guide = javaClass.classLoader.getResourceAsStream("marginalis/agent-guide.md")
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: return sendError(HttpResponseStatus.NOT_FOUND, "agent guide resource missing from this build", request, context)
        val bytes = guide.toByteArray(Charsets.UTF_8)
        val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(bytes))
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/markdown; charset=utf-8")
        sendResponse(request, context, response)
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
        // The plugin's own version — the installed truth, stamped into the
        // jar at build time (processResources), never a hardcoded constant.
        // Capability detection for agents: absence of a field can finally be
        // told apart from an old server that never heard of it. Not read from
        // the plugin manager: both platform lookups are internal API.
        pluginVersion?.let { addProperty("version", it) }
        ApplicationManager.getApplication().runReadAction {
            add("projects", openProjectsJson())
        }
    }

    /**
     * The open projects with their git branches. Branch is the discriminator
     * for same-layout worktrees — name and file layout are identical there
     * by construction, so it rides along in ping and resolution errors.
     */
    private fun openProjectsJson(): JsonArray = JsonArray().apply {
        for (project in ProjectManager.getInstance().openProjects) {
            if (project.isDisposed) continue
            add(
                JsonObject().apply {
                    addProperty("name", project.name)
                    val path = project.guessProjectDir()?.path ?: project.basePath
                    addProperty("path", path)
                    path?.let { p -> GitBranches.of(Path.of(p))?.let { addProperty("branch", it) } }
                },
            )
        }
    }

    /**
     * The posting author: agents may introduce themselves with
     * `author_name` (+ optional stable `author_id`); an unintroduced agent
     * is just "Agent". Applied wherever the agent writes or resolves.
     */
    private fun agentAuthor(json: JsonObject): Author.Agent {
        val name = json.stringOrNull("author_name")
        val id = json.stringOrNull("author_id")
        if (name == null && id == null) return Authors.agent
        return Author.Agent(name ?: Authors.agent.displayName, id)
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
        val outcome = addComment(json)
        send(outcome.json, outcome.status, request, context)
    }

    /**
     * A review round's worth of notes in one call. Each item is a whole
     * comment_add payload and is judged on its own: one stale anchor fails
     * its own item and the rest still land, because the alternative — the
     * caller unpicking which of nine notes survived a single 409 — is worse
     * than the round trip it saves.
     *
     * The envelope's `author_name`/`author_id`/`project` are defaults for
     * every item, since a batch almost always shares all three; an item that
     * states its own wins. Only a broken envelope is an HTTP error: with a
     * well-formed one the answer is 200 and the failures are inside it, in
     * request order, each item's shape identical to what the single call
     * would have sent.
     */
    private fun handleCommentAddBatch(json: JsonObject, request: FullHttpRequest, context: ChannelHandlerContext) {
        val items = json.get("items")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: return sendError(
                HttpResponseStatus.BAD_REQUEST,
                "missing 'items': comment_add_batch takes {\"items\": [ … ]}, an array of comment_add payloads. " +
                    "Top-level 'author_name', 'author_id' and 'project' apply to every item unless the item says " +
                    "otherwise.",
                request, context,
            )
        val results = JsonArray()
        var created = 0
        for (element in items) {
            if (!element.isJsonObject) {
                results.add(
                    JsonObject().apply {
                        addProperty("error", "each item must be a JSON object — one comment_add payload per item.")
                    },
                )
                continue
            }
            val outcome = addComment(withBatchDefaults(element.asJsonObject, json))
            if (outcome.status == HttpResponseStatus.OK) created++
            results.add(outcome.json)
        }
        sendJson(
            JsonObject().apply {
                add("results", results)
                addProperty("created", created)
            },
            request, context,
        )
    }

    /** The envelope's shared identity and scope, filled in wherever an item left them out. */
    private fun withBatchDefaults(item: JsonObject, envelope: JsonObject): JsonObject {
        val merged = item.deepCopy()
        for (field in BATCH_DEFAULTS) {
            if (!merged.has(field) && envelope.has(field)) merged.add(field, envelope.get(field))
        }
        return merged
    }

    /** What one add came to: the JSON to report for it, and the status it deserves. */
    private class AddOutcome(val status: HttpResponseStatus, val json: JsonObject) {
        companion object {
            fun created(json: JsonObject) = AddOutcome(HttpResponseStatus.OK, json)
            fun refused(status: HttpResponseStatus, message: String) =
                AddOutcome(status, JsonObject().apply { addProperty("error", message) })
        }
    }

    /**
     * One note, start to finish — the whole of comment_add, minus the
     * transport. It answers with an outcome instead of writing a response so
     * that the batch can collect many of them: a per-item failure there is a
     * result, not the end of the call, and both paths must judge anchors by
     * exactly the same rules or the batch becomes a second contract.
     */
    private fun addComment(json: JsonObject): AddOutcome {
        payloadError(json)?.let { return AddOutcome.refused(HttpResponseStatus.BAD_REQUEST, it) }
        val body = json.stringOrNull("body")
            ?: return AddOutcome.refused(
                HttpResponseStatus.BAD_REQUEST,
                "missing 'body': a thread is something said about code. Pass the note itself as 'body'.",
            )
        val anchorText = json.stringOrNull("anchor_text")
        // Each omission widens the subject: no 'line' means the file, no
        // 'file' either means the project. Garbage is still a mistake worth
        // naming, and so is a line with no file to be in.
        val file = json.stringOrNull("file")
        val line1 = json.intOrNull("line")
        anchorIntentError(json, file, anchorText)?.let {
            return AddOutcome.refused(HttpResponseStatus.BAD_REQUEST, it)
        }
        val order = json.intOrNull("order")
        val walkthroughLabel = json.stringOrNull("walkthrough")
        val projectFilter = json.stringOrNull("project")
        // Garbage in either vocabulary gets a teaching 400, not a silently
        // unmarked thread. The two are independent: any intent, any severity.
        val severity = when (val parsed = Severity.parse(json.stringOrNull("severity"))) {
            is Severity.Parsed.Invalid -> return AddOutcome.refused(HttpResponseStatus.BAD_REQUEST, parsed.reason)
            is Severity.Parsed.Ok -> parsed.severity
        }
        val intent = when (val parsed = Intent.parse(json.stringOrNull("intent"))) {
            is Intent.Parsed.Invalid -> return AddOutcome.refused(HttpResponseStatus.BAD_REQUEST, parsed.reason)
            is Intent.Parsed.Ok -> parsed.intent
        }

        // A path resolves the project by itself; without one, the caller has
        // to say which workspace they mean.
        val resolved = ApplicationManager.getApplication().runReadAction(
            Computable { if (file == null) resolveProject(projectFilter)?.to(null) else resolveFile(file, projectFilter) },
        ) ?: return AddOutcome(
            HttpResponseStatus.NOT_FOUND,
            resolutionErrorJson(
                if (file == null) projectResolutionFailure(projectFilter) else resolutionFailure(file, projectFilter),
            ),
        )
        val project = resolved.first
        val vFile = resolved.second

        var error: String? = null
        var errorStatus = HttpResponseStatus.BAD_REQUEST
        var thread: CommentThread? = null
        var adjusted = false

        ApplicationManager.getApplication().invokeAndWait {
            // Agents never create segments — the selection gesture is human.
            val created = if (line1 == null || file == null || vFile == null) {
                // Nothing to resolve against text, nothing to mark in it: the
                // subject is the file as a whole, or the project itself.
                CommentThread(
                    file, line = null, anchorText = null,
                    order = order, walkthrough = walkthroughLabel, severity = severity, intent = intent,
                )
            } else {
                val document = FileDocumentManager.getInstance().getDocument(vFile)
                if (document == null) {
                    error = "'$file' has no text document (binary or too large?)"
                    return@invokeAndWait
                }
                val placed = when (val outcome = resolveAnchoredLine(document, file, line1, anchorText)) {
                    is AnchorOutcome.Stale -> {
                        error = outcome.message
                        errorStatus = HttpResponseStatus.CONFLICT
                        return@invokeAndWait
                    }
                    is AnchorOutcome.Placed -> outcome
                }
                adjusted = placed.adjusted
                CommentThread(
                    file, placed.line0, lineText(document, placed.line0),
                    order = order, walkthrough = walkthroughLabel, severity = severity, intent = intent,
                ).also { MarginalisMarkers.attach(project, it, document) }
            }
            val author = agentAuthor(json)
            created.addMessage(Message(author, body))
            MarginalisStore.getInstance(project).threads.add(created)
            maybeNotify(project, created, author, body)
            thread = created
        }

        val added = thread ?: return AddOutcome.refused(errorStatus, error ?: "internal error")
        return AddOutcome.created(
            JsonObject().apply {
                addProperty("thread_id", added.id)
                // Each response says only what the thread actually has: no
                // file when the project is the subject, and no line — nor
                // anything about adjusting one — above the line.
                added.file?.let { addProperty("file", it) }
                added.line?.let {
                    addProperty("line", it + 1)
                    addProperty("line_adjusted", adjusted)
                }
                addProperty("status", added.status.kind.name.lowercase())
            },
        )
    }

    /**
     * Every field of an add, checked for shape before anything is created —
     * and every rejection written the way the severity 400 is: name the
     * field, say what is wrong with it, say what to do instead. A caller who
     * mistypes a payload gets a fix, not a status code. Null when the shape
     * is sound; the meaning of the values is judged after this.
     */
    private fun payloadError(json: JsonObject): String? {
        for ((field, what) in TEXT_FIELDS) {
            if (json.has(field) && json.stringOrNull(field) == null) {
                return "'$field' must be a string — $what."
            }
        }
        if (json.has("body") && json.stringOrNull("body")?.isBlank() == true) {
            return "'body' is empty: a thread with nothing said in it is not worth anchoring. Pass the note text."
        }
        if (json.has("order") && json.intOrNull("order") == null) {
            return "'order' must be an integer (1, 2, 3 …) — a step's position in a walkthrough. Omit it for an " +
                "ordinary thread."
        }
        return null
    }

    /**
     * The rules about what may be left out, shared by comment_add and
     * navigate. Omissions widen the subject — no `line` means the file, no
     * `file` means the project — so each one drops what the narrower rung
     * needed: anchor text has nothing to verify against without a line, and
     * a line has nowhere to be without a file. Garbage in `line` is a
     * mistake, not an omission. Null when the request is coherent.
     */
    private fun anchorIntentError(json: JsonObject, file: String?, anchorText: String?): String? = when {
        json.has("line") && json.intOrNull("line") == null ->
            "'line' must be an integer (1-based) — omit it entirely to address the file as a whole."
        file == null && json.intOrNull("line") != null ->
            "'line' without 'file': a line is a place in a file. Pass the 'file' it belongs to, or drop " +
                "'line' too to address the project as a whole."
        !json.has("line") && anchorText != null ->
            "'anchor_text' without 'line': there is nothing to anchor to. Pass the 'line' (1-based) it " +
                "belongs to, or drop 'anchor_text' to address the file as a whole."
        else -> null
    }

    private fun lineText(document: Document, line: Int): String =
        document.getText(TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)))

    /**
     * The turn signal's long arm: a balloon when an agent message lands in
     * a file the human doesn't have in front of them (the gutter and tab
     * glyph already cover the visible file). One notification per message,
     * click to open the thread; the settings page holds the off-switch.
     */
    private fun maybeNotify(project: Project, thread: CommentThread, author: Author, body: String) {
        if (!MarginalisSettings.getInstance().state.notifyOnAgentReply) return
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            val selectedFile = FileEditorManager.getInstance(project).selectedTextEditor
                ?.let { FileDocumentManager.getInstance().getFile(it.document) }
            val selectedRel = selectedFile?.let { file ->
                project.guessProjectDir()?.let { base -> VfsUtilCore.getRelativePath(file, base) }
            }
            // A project-level thread is on nobody's screen, so it always rings.
            if (thread.file != null && selectedRel == thread.file) return@invokeLater // already on screen
            val where = thread.file?.plus(thread.line?.let { ":${it + 1}" } ?: "") ?: project.name
            NotificationGroupManager.getInstance().getNotificationGroup("Marginalis")
                .createNotification(
                    "${author.displayName} · $where",
                    StringUtil.shortenTextWithEllipsis(MarkdownRenderer.previewText(body), 120, 0),
                    NotificationType.INFORMATION,
                )
                .addAction(
                    NotificationAction.createSimpleExpiring("Open Thread") {
                        WalkthroughNavigator.navigateTo(project, thread)
                    },
                )
                .notify(project)
        }
    }

    /** Outcome of placing a 1-based line hint + anchor text against a live document. */
    private sealed class AnchorOutcome {
        class Placed(val line0: Int, val adjusted: Boolean) : AnchorOutcome()
        class Stale(val message: String) : AnchorOutcome()
    }

    /**
     * The anchoring contract, shared by every endpoint that takes {file,
     * line, anchor_text}, is core's AnchorPolicy.resolveHint; this maps its
     * outcomes to transport terms. Stale means 409 — the caller should
     * re-read the file, never land somewhere wrong silently.
     */
    private fun resolveAnchoredLine(document: Document, file: String, line1: Int, anchorText: String?): AnchorOutcome =
        when (
            val resolved = AnchorPolicy.resolveHint(
                lineCount = document.lineCount,
                lineTextAt = { lineText(document, it) },
                hintLine = line1 - 1,
                anchorText = anchorText,
            )
        ) {
            is AnchorPolicy.HintResolution.Placed -> AnchorOutcome.Placed(resolved.line, resolved.adjusted)
            is AnchorPolicy.HintResolution.OutOfRange -> AnchorOutcome.Stale(
                "line $line1 out of range: '$file' has ${resolved.lineCount} lines. Re-read the file.",
            )
            AnchorPolicy.HintResolution.NoMatch -> AnchorOutcome.Stale(
                "anchor_text does not match line $line1 or the ±${AnchorPolicy.SEARCH_WINDOW} lines " +
                    "around it. The file has probably changed — re-read it.",
            )
        }

    // -------------------------------------------------------------- reply

    private fun handleCommentReply(json: JsonObject, request: FullHttpRequest, context: ChannelHandlerContext) {
        val (project, thread) = lookupThread(json, request, context) ?: return
        val body = json.stringOrNull("body")
            ?: return sendError(HttpResponseStatus.BAD_REQUEST, "missing 'body'", request, context)
        val message = Message(agentAuthor(json), body)
        thread.addMessage(message)
        MarginalisStore.getInstance(project).threads.notifyChanged(thread)
        maybeNotify(project, thread, message.author, body)
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
        if (resolve) thread.resolve(agentAuthor(json)) else thread.reopen()
        MarginalisStore.getInstance(project).threads.notifyChanged(thread)
        sendJson(
            JsonObject().apply {
                addProperty("thread_id", thread.id)
                addProperty("status", thread.status.kind.name.lowercase())
            },
            request, context,
        )
    }

    /**
     * Orphan rescue — agent-side, per the asymmetry: the human never does
     * anchor surgery. Only ORPHANED threads may be re-anchored (a live
     * anchor doesn't move: 409), same file only for now, and the target is
     * verified with the same anchoring contract as comment_add — a rescued
     * thread reopens with a fresh marker, never lands somewhere guessed.
     */
    private fun handleReanchor(json: JsonObject, request: FullHttpRequest, context: ChannelHandlerContext) {
        val (project, thread) = lookupThread(json, request, context) ?: return
        if (thread.line == null) {
            val subject = if (thread.isProjectLevel) "the project" else "the file"
            return sendError(
                HttpResponseStatus.BAD_REQUEST,
                "thread '${thread.id}' has no anchor to re-find — it is about $subject as a whole. A " +
                    "file-level thread orphans only when its file disappears, and reopens by itself when " +
                    "the path comes back; a project-level thread never orphans.",
                request, context,
            )
        }
        val line1 = json.intOrNull("line")
            ?: return sendError(HttpResponseStatus.BAD_REQUEST, "missing or non-integer 'line' (1-based)", request, context)
        if (thread.status !is ThreadStatus.Orphaned) {
            return sendError(
                HttpResponseStatus.CONFLICT,
                "thread '${thread.id}' is ${thread.status.kind.name.lowercase()}, not orphaned — live anchors don't move.",
                request, context,
            )
        }
        json.stringOrNull("file")?.takeIf { it != thread.file }?.let {
            return sendError(
                HttpResponseStatus.BAD_REQUEST,
                "cross-file re-anchor isn't supported (yet) — the thread belongs to '${thread.file}'.",
                request, context,
            )
        }
        val anchorText = json.stringOrNull("anchor_text")
        // Core's invariant: a thread with a line has the file it is in, so
        // the guard above has already ruled this out.
        val path = thread.file
            ?: return sendError(HttpResponseStatus.BAD_REQUEST, "thread '${thread.id}' has no file", request, context)
        val vFile = ApplicationManager.getApplication()
            .runReadAction(Computable { resolveFile(path, json.stringOrNull("project"))?.second })
            ?: return sendResolutionError(resolutionFailure(path, json.stringOrNull("project")), request, context)

        var error: String? = null
        var errorStatus = HttpResponseStatus.BAD_REQUEST
        var landed = -1
        ApplicationManager.getApplication().invokeAndWait {
            val document = FileDocumentManager.getInstance().getDocument(vFile)
            if (document == null) {
                error = "'$path' has no text document (binary or too large?)"
                return@invokeAndWait
            }
            val placed = when (val outcome = resolveAnchoredLine(document, path, line1, anchorText)) {
                is AnchorOutcome.Stale -> {
                    error = outcome.message
                    errorStatus = HttpResponseStatus.CONFLICT
                    return@invokeAndWait
                }
                is AnchorOutcome.Placed -> outcome
            }
            thread.rescueTo(placed.line0, lineText(document, placed.line0))
            MarginalisMarkers.attach(project, thread, document)
            MarginalisStore.getInstance(project).threads.notifyChanged(thread)
            landed = placed.line0
        }
        if (error != null || landed < 0) {
            return sendError(errorStatus, error ?: "internal error", request, context)
        }
        sendJson(
            JsonObject().apply {
                addProperty("thread_id", thread.id)
                addProperty("line", landed + 1)
                addProperty("status", thread.status.kind.name.lowercase())
            },
            request, context,
        )
    }

    /**
     * Rescue every orphan on one file at once. A whole-file rewrite orphans
     * all of them together, and recovering them one at a time is the same
     * read-find-call cycle repeated — so the search runs here, over the file
     * the caller names.
     *
     * Two things differ from the single rescue, both because the situation
     * differs: the caller supplies no line hint (there is nothing sensible
     * to hint after a rewrite), and the content search widens to the whole
     * file rather than ±20 lines — the old line number is worthless, the
     * anchor text is not. Everything else holds: only orphans move, and only
     * to content that actually matches, so a thread whose text is genuinely
     * gone stays orphaned and says so. No orphans on the file is not a
     * failure; it is an empty list.
     */
    private fun handleReanchorAll(json: JsonObject, request: FullHttpRequest, context: ChannelHandlerContext) {
        val file = json.stringOrNull("file")
            ?: return sendError(
                HttpResponseStatus.BAD_REQUEST,
                "missing 'file': comment_reanchor_all rescues the orphans of one file. Pass its project-relative " +
                    "path, e.g. 'src/App.kt'.",
                request, context,
            )
        val projectFilter = json.stringOrNull("project")
        val (project, vFile) = ApplicationManager.getApplication()
            .runReadAction(Computable { resolveFile(file, projectFilter) })
            ?: return sendResolutionError(resolutionFailure(file, projectFilter), request, context)

        val results = JsonArray()
        var rescued = 0
        var error: String? = null
        ApplicationManager.getApplication().invokeAndWait {
            val document = FileDocumentManager.getInstance().getDocument(vFile)
            if (document == null) {
                error = "'$file' has no text document (binary or too large?)"
                return@invokeAndWait
            }
            val store = MarginalisStore.getInstance(project)
            // Only threads with an anchor to find: a file-level orphan is
            // waiting for its file, not for its content.
            val orphans = store.threads.all()
                .filter { it.file == file && it.line != null && it.status is ThreadStatus.Orphaned }
            for (thread in orphans) {
                val found = AnchorPolicy.findAnchor(
                    lineCount = document.lineCount,
                    lineTextAt = { lineText(document, it) },
                    nearLine = thread.line ?: 0,
                    anchorText = thread.anchorText ?: "",
                    segment = thread.segment,
                    window = document.lineCount,
                )
                if (found == null) {
                    results.add(
                        JsonObject().apply {
                            addProperty("thread_id", thread.id)
                            addProperty("status", thread.status.kind.name.lowercase())
                        },
                    )
                    continue
                }
                thread.rescueTo(found.line, lineText(document, found.line))
                MarginalisMarkers.attach(project, thread, document)
                store.threads.notifyChanged(thread)
                rescued++
                results.add(
                    JsonObject().apply {
                        addProperty("thread_id", thread.id)
                        addProperty("line", found.line + 1)
                        addProperty("status", thread.status.kind.name.lowercase())
                    },
                )
            }
        }
        error?.let { return sendError(HttpResponseStatus.BAD_REQUEST, it, request, context) }
        sendJson(
            JsonObject().apply {
                addProperty("file", file)
                add("results", results)
                addProperty("rescued", rescued)
            },
            request, context,
        )
    }

    /** Bulk resolve, optionally scoped to one file: {"file"?}. */
    private fun handleResolveAll(json: JsonObject, request: FullHttpRequest, context: ChannelHandlerContext) {
        val fileFilter = json.stringOrNull("file")
        val resolver = agentAuthor(json)
        var resolved = 0
        for (project in ProjectManager.getInstance().openProjects) {
            if (project.isDisposed) continue
            val store = MarginalisStore.getInstance(project)
            for (thread in store.threads.all()) {
                if (fileFilter != null && thread.file != fileFilter) continue
                if (thread.status is ThreadStatus.Resolved) continue
                thread.resolve(resolver)
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
        // "All the open guidance for the file I am about to edit" — the
        // query this filter exists for.
        val intentFilter = when (val parsed = Intent.parse(params["intent"]?.firstOrNull())) {
            is Intent.Parsed.Invalid -> return sendError(HttpResponseStatus.BAD_REQUEST, parsed.reason, request, context)
            is Intent.Parsed.Ok -> parsed.intent
        }
        val unreadOnly = params["unread_only"]?.firstOrNull()?.toBoolean() ?: false
        val projectFilter = params["project"]?.firstOrNull()
        // The sweep cursor: hand back the newest 'updated_at' you saw and
        // get only what has moved since. Strictly after, so nothing repeats.
        val updatedAfter = params["updated_after"]?.firstOrNull()?.let {
            try {
                Instant.parse(it)
            } catch (e: DateTimeParseException) {
                return sendError(
                    HttpResponseStatus.BAD_REQUEST,
                    "'updated_after' must be an ISO-8601 instant (e.g. 2026-08-14T09:30:00Z) — pass back the " +
                        "'updated_at' of the newest thread your last listing returned.",
                    request, context,
                )
            }
        }
        // The caller's read receipts are their own: identity via the same
        // author params (query-string here), anonymous callers share "Agent".
        val callerKey = (params["author_id"]?.firstOrNull() ?: params["author_name"]?.firstOrNull())
            ?: Authors.agent.receiptKey

        val threadsJson = JsonArray()
        var markedSeen = 0
        val timeFormat = DateTimeFormatter.ISO_INSTANT

        ApplicationManager.getApplication().runReadAction {
            for (project in ProjectManager.getInstance().openProjects) {
                if (project.isDisposed) continue
                if (projectFilter != null && !projectMatches(project, projectFilter)) continue
                val store = MarginalisStore.getInstance(project)
                val listed = store.threads
                    .query(fileFilter, statusFilter, intentFilter, if (unreadOnly) callerKey else null, updatedAfter)
                    .sortedWith(ThreadOrder.byAnchor)
                for (thread in listed) {
                    val messagesJson = JsonArray()
                    for (message in thread.messages) {
                        val newlySeen = !message.seenBy(callerKey)
                        if (newlySeen) {
                            message.markSeenBy(callerKey)
                            markedSeen++
                        }
                        messagesJson.add(
                            JsonObject().apply {
                                addProperty("message_id", message.id)
                                add("author", authorJson(message.author))
                                addProperty("body", message.body)
                                addProperty("created_at", timeFormat.format(message.createdAt))
                                add("seen_by", JsonArray().apply { message.seenBy.sorted().forEach(::add) })
                                if (newlySeen) addProperty("newly_seen", true)
                            },
                        )
                    }
                    threadsJson.add(
                        JsonObject().apply {
                            addProperty("thread_id", thread.id)
                            addProperty("project", project.name)
                            // Absent as the subject widens: no line once the
                            // file is the address, no file once the project is.
                            thread.file?.let { addProperty("file", it) }
                            val line = store.currentLine(thread)
                            line?.let { addProperty("line", it + 1) }
                            // What that line says NOW: compare it with the
                            // anchor_text you wrote and you know whether the
                            // code moved under the thread — without re-reading
                            // the file.
                            currentAnchorText(project, thread, line)?.let { addProperty("anchor_text", it) }
                            addProperty("status", thread.status.kind.name.lowercase())
                            addProperty("created_at", timeFormat.format(thread.createdAt))
                            addProperty("updated_at", timeFormat.format(thread.updatedAt))
                            // Additive: the human anchored this thread to a
                            // span within the line, not the whole line.
                            thread.segment?.let { seg ->
                                add(
                                    "segment",
                                    JsonObject().apply {
                                        addProperty("exact", seg.exact)
                                        if (seg.prefix.isNotEmpty()) addProperty("prefix", seg.prefix)
                                        if (seg.suffix.isNotEmpty()) addProperty("suffix", seg.suffix)
                                    },
                                )
                            }
                            thread.order?.let { addProperty("order", it) }
                            thread.walkthrough?.let { addProperty("walkthrough", it) }
                            thread.severity?.let { addProperty("severity", it.name.lowercase()) }
                            thread.intent?.let { addProperty("intent", it.name.lowercase()) }
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

    // ----------------------------------------------------------- navigate

    /**
     * Ephemeral pointing: open the file and put the caret on the line,
     * creating no artifact — threads are for things worth saying, navigate
     * is for things worth seeing. Fires only on explicit user request
     * (etiquette lives in the agent-facing skill); the settings page holds
     * the hard off-switch, surfaced honestly as a 403 so the agent can tell
     * the user why nothing happened. Full navigation with focus is correct
     * BECAUSE every call is user-solicited.
     */
    private fun handleNavigate(json: JsonObject, request: FullHttpRequest, context: ChannelHandlerContext) {
        val file = json.stringOrNull("file")
            ?: return sendError(HttpResponseStatus.BAD_REQUEST, "missing 'file' (project-relative path)", request, context)
        val anchorText = json.stringOrNull("anchor_text")
        // No line means "just show me the file" — same omission rule as
        // comment_add's file-level threads.
        val line1 = json.intOrNull("line")
        anchorIntentError(json, file, anchorText)?.let {
            return sendError(HttpResponseStatus.BAD_REQUEST, it, request, context)
        }
        val projectFilter = json.stringOrNull("project")

        if (!MarginalisSettings.getInstance().state.navigationEnabled) {
            return sendError(HttpResponseStatus.FORBIDDEN, "navigation is disabled in Marginalis settings", request, context)
        }

        val (project, vFile) = ApplicationManager.getApplication()
            .runReadAction(Computable { resolveFile(file, projectFilter) })
            ?: return sendResolutionError(resolutionFailure(file, projectFilter), request, context)

        var error: String? = null
        var errorStatus = HttpResponseStatus.BAD_REQUEST
        var landedLine0 = -1
        var adjusted = false

        ApplicationManager.getApplication().invokeAndWait {
            if (line1 == null) {
                // The file is the destination: open it at the top.
                OpenFileDescriptor(project, vFile, 0, 0).navigate(true)
                landedLine0 = 0
                return@invokeAndWait
            }
            val document = FileDocumentManager.getInstance().getDocument(vFile)
            if (document == null) {
                error = "'$file' has no text document (binary or too large?)"
                return@invokeAndWait
            }
            val placed = when (val outcome = resolveAnchoredLine(document, file, line1, anchorText)) {
                is AnchorOutcome.Stale -> {
                    error = outcome.message
                    errorStatus = HttpResponseStatus.CONFLICT
                    return@invokeAndWait
                }
                is AnchorOutcome.Placed -> outcome
            }
            OpenFileDescriptor(project, vFile, placed.line0, 0).navigate(true)
            landedLine0 = placed.line0
            adjusted = placed.adjusted
        }

        if (error != null || landedLine0 < 0) {
            return sendError(errorStatus, error ?: "internal error", request, context)
        }
        sendJson(
            JsonObject().apply {
                addProperty("navigated", true)
                addProperty("file", file)
                // Nothing was asked of a line, so nothing is claimed about one.
                if (line1 != null) {
                    addProperty("line", landedLine0 + 1)
                    addProperty("line_adjusted", adjusted)
                }
            },
            request, context,
        )
    }

    /**
     * The anchor line's text as it stands right now — the answer to "is this
     * still what I wrote?" without a re-read. Live from the document when one
     * is loaded (an in-place edit shows up here while the stored fingerprint
     * still says what the thread was born on); otherwise the fingerprint
     * itself, which is the most the server honestly knows about a file
     * nobody has open. Null above the line, where no text is the subject.
     */
    private fun currentAnchorText(project: Project, thread: CommentThread, line: Int?): String? {
        if (line == null) return null
        val vFile = thread.file?.let { project.guessProjectDir()?.findFileByRelativePath(it) }
        val document = vFile?.let { FileDocumentManager.getInstance().getCachedDocument(it) }
            ?: return thread.anchorText
        if (line >= document.lineCount) return thread.anchorText
        return lineText(document, line)
    }

    private fun authorJson(author: Author): JsonObject = JsonObject().apply {
        addProperty("kind", if (author is Author.Agent) "agent" else "user")
        addProperty("name", author.displayName)
        (author as? Author.Agent)?.id?.let { addProperty("id", it) }
    }

    // ------------------------------------------------------------ helpers

    /**
     * Resolve a project-relative path; first match wins among the projects
     * [projectFilter] admits (all of them when null). With same-layout
     * worktrees open, first-match is ambiguous by construction — the filter
     * is the caller's way out, and resolution failures list the open
     * projects with branches so the caller can pick.
     */
    private fun resolveFile(relPath: String, projectFilter: String? = null): Pair<Project, VirtualFile>? {
        for (project in ProjectManager.getInstance().openProjects) {
            if (project.isDisposed) continue
            if (projectFilter != null && !projectMatches(project, projectFilter)) continue
            val base = project.guessProjectDir() ?: continue
            val vFile = base.findFileByRelativePath(relPath) ?: continue
            return project to vFile
        }
        return null
    }

    /**
     * The project a file-less call is about. A path normally answers this by
     * itself; with nothing to resolve by, the caller must name the workspace
     * whenever more than one is open — guessing would file the thread in
     * someone else's project.
     */
    private fun resolveProject(projectFilter: String?): Project? {
        val open = ProjectManager.getInstance().openProjects.filter { !it.isDisposed }
        if (projectFilter == null) return open.singleOrNull()
        return open.firstOrNull { projectMatches(it, projectFilter) }
    }

    /** Match by project name, full root path, or the root's trailing segment. */
    private fun projectMatches(project: Project, filter: String): Boolean {
        if (project.name == filter) return true
        val path = project.guessProjectDir()?.path ?: project.basePath ?: return false
        return path == filter || path.endsWith("/$filter")
    }

    private fun projectResolutionFailure(projectFilter: String?): String =
        if (projectFilter == null) {
            "a thread about the project needs to know which one: pass 'project' (name or root path). " +
                "There is no file here to resolve it by — see open_projects."
        } else {
            "no open project matches '$projectFilter' — see open_projects."
        }

    private fun resolutionFailure(file: String, projectFilter: String?): String =
        if (projectFilter == null) {
            "'$file' not found in any open project (paths are project-relative). " +
                "If several open projects share this layout, pass 'project' — see open_projects."
        } else {
            "'$file' not found in a project matching '$projectFilter' — see open_projects."
        }

    /** The teaching part of a resolution failure: the error plus every open project with its branch. */
    private fun resolutionErrorJson(message: String): JsonObject = JsonObject().apply {
        addProperty("error", message)
        add("open_projects", ApplicationManager.getApplication().runReadAction(Computable { openProjectsJson() }))
    }

    /** 404 that teaches: the error plus every open project with its branch. */
    private fun sendResolutionError(message: String, request: FullHttpRequest, context: ChannelHandlerContext) {
        send(resolutionErrorJson(message), HttpResponseStatus.NOT_FOUND, request, context)
    }

    private companion object {
        /** What a batch envelope may say once on behalf of every item in it. */
        val BATCH_DEFAULTS = listOf("author_name", "author_id", "project")

        /** Every add field that must be text, and what it is for — used to write its 400. */
        val TEXT_FIELDS = listOf(
            "file" to "the project-relative path, e.g. 'src/App.kt'. Omit it to address the project as a whole",
            "body" to "the note itself",
            "anchor_text" to "the exact text you believe occupies the line",
            "walkthrough" to "a short label, e.g. 'A', grouping the steps of one walk",
            "project" to "a project name or root path, as listed by ping",
            "author_name" to "how you want to be shown in the margin",
            "author_id" to "your stable identity, which read receipts are keyed by",
            "severity" to "exactly 'blocker' or 'nit'",
            "intent" to "exactly 'finding', 'guidance' or 'question'",
        )
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
