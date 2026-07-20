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
        addProperty("line", thread.line)
        addProperty("anchor_text", thread.anchorText)
        thread.order?.let { addProperty("order", it) }
        thread.walkthrough?.let { addProperty("walkthrough", it) }
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
                            addProperty("seen_by_agent", m.seenByAgent)
                        },
                    )
                }
            },
        )
    }

    private fun thread(json: JsonObject): CommentThread {
        val thread = CommentThread(
            file = json.get("file").asString,
            line = json.get("line").asInt,
            anchorText = json.get("anchor_text")?.asString ?: "",
            id = json.get("id").asString,
            createdAt = Instant.parse(json.get("created_at").asString),
            order = json.get("order")?.takeIf { it.isJsonPrimitive }?.asInt,
            // "walkthrough"; pre-rename files wrote "tour" — both mean the label.
            walkthrough = (json.get("walkthrough") ?: json.get("tour"))
                ?.takeIf { it.isJsonPrimitive }?.asString,
        )
        for (m in json.getAsJsonArray("messages")) {
            val msg = m.asJsonObject
            thread.addMessage(
                Message(
                    author = author(msg.getAsJsonObject("author")),
                    body = msg.get("body").asString,
                    id = msg.get("id").asString,
                    createdAt = Instant.parse(msg.get("created_at").asString),
                    seenByAgent = msg.get("seen_by_agent")?.asBoolean,
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
