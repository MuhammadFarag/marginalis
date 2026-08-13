package dev.marginalis.core

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.Instant

/**
 * Threads ↔ JSON, as strings. File I/O and storage location are caller
 * concerns; this codec only defines the format — including tolerance for
 * values written by earlier versions (author kind "HUMAN" predates the
 * User/Agent hierarchy).
 */
object ThreadsCodec {

    fun encode(threads: List<CommentThread>): String {
        val root = JsonObject().apply {
            addProperty("version", 1)
            add("threads", JsonArray().apply { threads.forEach { add(threadJson(it)) } })
        }
        return root.toString()
    }

    fun decode(text: String): List<CommentThread> {
        val root = JsonParser.parseString(text).asJsonObject
        return root.getAsJsonArray("threads").map { thread(it.asJsonObject) }
    }

    private fun threadJson(thread: CommentThread): JsonObject = JsonObject().apply {
        addProperty("id", thread.id)
        addProperty("file", thread.file)
        // A file-level thread writes neither — absence IS the shape, and it
        // reads back as one (see [thread]).
        thread.line?.let { addProperty("line", it) }
        thread.anchorText?.let { addProperty("anchor_text", it) }
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
        thread.severity?.let { addProperty("severity", it.name) }
        addProperty("status", thread.status.kind.name)
        addProperty("created_at", thread.createdAt.toString())
        thread.resolvedBy?.let { add("resolved_by", authorJson(it)) }
        add(
            "messages",
            JsonArray().apply {
                for (m in thread.messages) {
                    add(
                        JsonObject().apply {
                            addProperty("id", m.id)
                            add("author", authorJson(m.author))
                            addProperty("body", m.body)
                            addProperty("created_at", m.createdAt.toString())
                            add("seen_by", JsonArray().apply { m.seenBy.sorted().forEach(::add) })
                        },
                    )
                }
            },
        )
    }

    private fun thread(json: JsonObject): CommentThread {
        // No "line" means file-level. A line without "anchor_text" is not
        // the same thing — it's a pre-anchor-text thread, and it keeps its
        // line with an empty fingerprint rather than losing its place.
        val line = json.get("line")?.takeIf { it.isJsonPrimitive }?.asInt
        val thread = CommentThread(
            file = json.get("file").asString,
            line = line,
            anchorText = json.get("anchor_text")?.takeIf { it.isJsonPrimitive }?.asString ?: line?.let { "" },
            id = json.get("id").asString,
            createdAt = Instant.parse(json.get("created_at").asString),
            order = json.get("order")?.takeIf { it.isJsonPrimitive }?.asInt,
            // "walkthrough"; pre-rename files wrote "tour" — both mean the label.
            walkthrough = (json.get("walkthrough") ?: json.get("tour"))
                ?.takeIf { it.isJsonPrimitive }?.asString,
            // Additive: pre-segment files simply have whole-line threads.
            segment = json.get("segment")?.takeIf { it.isJsonObject }?.asJsonObject?.let { seg ->
                seg.get("exact")?.takeIf { it.isJsonPrimitive }?.asString?.let { exact ->
                    Segment(
                        exact = exact,
                        prefix = seg.get("prefix")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
                        suffix = seg.get("suffix")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
                    )
                }
            },
            // Additive: pre-severity files are ordinary comments; the one
            // shared vocabulary (Severity.parse), leniently — unknown
            // values load as unmarked rather than failing the whole file.
            severity = Severity.parseLenient(json.get("severity")?.takeIf { it.isJsonPrimitive }?.asString),
        )
        for (m in json.getAsJsonArray("messages")) {
            val msg = m.asJsonObject
            thread.addMessage(
                Message(
                    author = author(msg.getAsJsonObject("author")),
                    body = msg.get("body").asString,
                    id = msg.get("id").asString,
                    createdAt = Instant.parse(msg.get("created_at").asString),
                    seenBy = seenBy(msg),
                ),
            )
        }
        val resolvedBy = json.get("resolved_by")?.takeIf { it.isJsonObject }?.let { author(it.asJsonObject) }
        thread.restoreStatus(
            when (json.get("status").asString.uppercase()) {
                "RESOLVED" -> ThreadStatus.Resolved(resolvedBy ?: Author.User("?"))
                "ORPHANED" -> ThreadStatus.Orphaned
                else -> ThreadStatus.Open
            },
        )
        return thread
    }

    /**
     * "seen_by" is a set of agent receipt keys; pre-multi-agent files wrote
     * a single "seen_by_agent" bit — true maps to the anonymous "Agent",
     * preserving "was read in the single-agent era" for the edit window.
     */
    private fun seenBy(msg: JsonObject): Set<String> {
        msg.get("seen_by")?.takeIf { it.isJsonArray }?.let { keys ->
            return keys.asJsonArray.mapNotNull { el -> el.takeIf { it.isJsonPrimitive }?.asString }.toSet()
        }
        return if (msg.get("seen_by_agent")?.takeIf { it.isJsonPrimitive }?.asBoolean == true) {
            setOf(Author.Agent.ANONYMOUS_NAME)
        } else {
            emptySet()
        }
    }

    private fun authorJson(author: Author): JsonObject = JsonObject().apply {
        when (author) {
            is Author.User -> addProperty("kind", "USER")
            is Author.Agent -> {
                addProperty("kind", "AGENT")
                author.id?.let { addProperty("id", it) }
            }
        }
        addProperty("name", author.displayName)
    }

    private fun author(json: JsonObject): Author {
        val name = json.get("name").asString
        return when (json.get("kind").asString.uppercase()) {
            "AGENT" -> Author.Agent(name, json.get("id")?.takeIf { it.isJsonPrimitive }?.asString)
            // "USER" and pre-rename "HUMAN" files both mean the local person.
            else -> Author.User(name)
        }
    }
}
