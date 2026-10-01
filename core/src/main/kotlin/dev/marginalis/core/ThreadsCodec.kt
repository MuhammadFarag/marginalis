package dev.marginalis.core

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.Instant

object ThreadsCodec {

    fun encode(threads: List<CommentThread>, handedBackAt: Instant? = null): String {
        val root = JsonObject().apply {
            addProperty("version", 1)
            handedBackAt?.let { addProperty("handed_back_at", it.toString()) }
            add("threads", JsonArray().apply { threads.forEach { add(threadJson(it)) } })
        }
        return root.toString()
    }

    data class Document(val threads: List<CommentThread>, val handedBackAt: Instant?) {
        companion object {
            val EMPTY = Document(emptyList(), null)
        }
    }

    fun decodeDocument(text: String): Document {
        val root = JsonParser.parseString(text).asJsonObject
        return Document(
            threads = root.getAsJsonArray("threads").map { thread(it.asJsonObject) },
            handedBackAt = root.get("handed_back_at")?.takeIf { it.isJsonPrimitive }?.asString?.let(::instantOrNull),
        )
    }

    fun decode(text: String): List<CommentThread> = decodeDocument(text).threads

    private fun instantOrNull(text: String): Instant? = runCatching { Instant.parse(text) }.getOrNull()

    private fun threadJson(thread: CommentThread): JsonObject = JsonObject().apply {
        addProperty("id", thread.id)
        // Absence is the shape: no file means project-level, no line means file-level.
        thread.file?.let { addProperty("file", it) }
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
        thread.intent?.let { addProperty("intent", it.name) }
        addProperty("status", thread.status.kind.name)
        addProperty("created_at", thread.createdAt.toString())
        addProperty("updated_at", thread.updatedAt.toString())
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
                            m.to?.let { addProperty("to", it.wire) }
                        },
                    )
                }
            },
        )
    }

    private fun thread(json: JsonObject): CommentThread {
        // A line without "anchor_text" is a legacy thread, not a file-level one:
        // it keeps its line with an empty fingerprint.
        val line = json.get("line")?.takeIf { it.isJsonPrimitive }?.asInt
        val thread = CommentThread(
            file = json.get("file")?.takeIf { it.isJsonPrimitive }?.asString,
            line = line,
            anchorText = json.get("anchor_text")?.takeIf { it.isJsonPrimitive }?.asString ?: line?.let { "" },
            id = json.get("id").asString,
            createdAt = Instant.parse(json.get("created_at").asString),
            order = json.get("order")?.takeIf { it.isJsonPrimitive }?.asInt,
            // Legacy files wrote "tour".
            walkthrough = (json.get("walkthrough") ?: json.get("tour"))
                ?.takeIf { it.isJsonPrimitive }?.asString,
            segment = json.get("segment")?.takeIf { it.isJsonObject }?.asJsonObject?.let { seg ->
                seg.get("exact")?.takeIf { it.isJsonPrimitive }?.asString?.let { exact ->
                    Segment(
                        exact = exact,
                        prefix = seg.get("prefix")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
                        suffix = seg.get("suffix")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
                    )
                }
            },
            severity = Severity.parseLenient(json.get("severity")?.takeIf { it.isJsonPrimitive }?.asString),
            intent = Intent.parseLenient(json.get("intent")?.takeIf { it.isJsonPrimitive }?.asString),
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
                    to = Addressee.parseLenient(msg.get("to")?.takeIf { it.isJsonPrimitive }?.asString),
                ),
            )
        }
        // Must follow the messages, which each count as a change while being added back.
        thread.restoreUpdatedAt(
            json.get("updated_at")?.takeIf { it.isJsonPrimitive }?.asString?.let { Instant.parse(it) }
                ?: thread.messages.maxOfOrNull { it.createdAt }
                ?: thread.createdAt,
        )
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

    /** Legacy single-agent files wrote a "seen_by_agent" bit instead. */
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
            // Also legacy "HUMAN".
            else -> Author.User(name)
        }
    }
}
