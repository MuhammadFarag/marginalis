package dev.marginalis.plugin.store

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * M2 durability (handover §7, §10): threads live in `.idea/marginalis.json`
 * — per-project, private notes, gitignored by the usual `.idea` rules.
 *
 * Only data is persisted, never anchors: RangeMarkers die with the Document,
 * and the file may have changed while the IDE was closed (git pull, branch
 * switch). Rehydrated threads re-anchor by content in MarginalisStartup.
 */
object MarginalisPersistence {
    private val log = logger<MarginalisPersistence>()

    private fun storageFile(project: Project): Path? =
        project.guessProjectDir()?.path?.let { Path.of(it, ".idea", "marginalis.json") }

    @Synchronized
    fun save(project: Project, threads: List<CommentThread>) {
        val path = storageFile(project) ?: return
        try {
            Files.createDirectories(path.parent)
            val root = JsonObject().apply {
                addProperty("version", 1)
                add(
                    "threads",
                    JsonArray().apply { threads.forEach { add(toJson(it)) } },
                )
            }
            Files.writeString(path, root.toString())
        } catch (e: Exception) {
            log.warn("Failed to save margin threads to $path", e)
        }
    }

    fun load(project: Project): List<CommentThread> {
        val path = storageFile(project) ?: return emptyList()
        if (!Files.exists(path)) return emptyList()
        return try {
            val root = JsonParser.parseString(Files.readString(path)).asJsonObject
            root.getAsJsonArray("threads").map { fromJson(it.asJsonObject) }
        } catch (e: Exception) {
            log.warn("Failed to load margin threads from $path — starting empty", e)
            emptyList()
        }
    }

    private fun toJson(thread: CommentThread): JsonObject = JsonObject().apply {
        addProperty("id", thread.id)
        addProperty("file", thread.file)
        addProperty("line", thread.currentLine())
        addProperty("anchor_text", thread.anchorText)
        addProperty("status", thread.status.name)
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

    private fun fromJson(json: JsonObject): CommentThread {
        val thread = CommentThread(
            file = json.get("file").asString,
            line = json.get("line").asInt,
            anchorText = json.get("anchor_text")?.asString ?: "",
            id = json.get("id").asString,
            createdAt = Instant.parse(json.get("created_at").asString),
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
        thread.restoreStatus(
            status = ThreadStatus.valueOf(json.get("status").asString),
            resolvedBy = json.get("resolved_by")?.takeIf { it.isJsonObject }?.let { author(it.asJsonObject) },
        )
        return thread
    }

    private fun authorJson(author: Author): JsonObject = JsonObject().apply {
        addProperty("kind", author.kind.name)
        addProperty("name", author.displayName)
    }

    private fun author(json: JsonObject): Author =
        Author(AuthorKind.valueOf(json.get("kind").asString), json.get("name").asString)
}
