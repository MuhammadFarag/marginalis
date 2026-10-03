package dev.marginalis.core

class People(rows: List<Row>) {

    enum class Kind {
        PERSON {
            override fun canonical(identity: String): String = GitHubLogin.bare(identity)

            override fun keyOf(identity: String): String = canonical(identity).lowercase()
        },
        AGENT {
            override fun canonical(identity: String): String = identity.trim()

            override fun keyOf(identity: String): String = canonical(identity)
        },
        ;

        abstract fun canonical(identity: String): String

        abstract fun keyOf(identity: String): String
    }

    data class Row(val identity: String, val kind: Kind, val nickname: String = "", val picture: String? = null) {
        val key: String get() = kind.keyOf(identity)

        fun face(): Face {
            val canonical = kind.canonical(identity)
            val name = nickname.trim().ifEmpty { canonical }
            val shown = picture.presentOrNull()
            return when (kind) {
                Kind.PERSON ->
                    Face(FaceKey.GitHub(canonical), name, canonical.ifEmpty { null }, avatarUrl = null, shown, agent = false)
                Kind.AGENT -> Face(FaceKey.Agent(canonical), name, login = null, avatarUrl = null, shown, agent = true)
            }
        }

        companion object {
            fun normalizedOrNull(identity: String, kind: Kind, nickname: String, picture: String?): Row? =
                Row(kind.canonical(identity), kind, nickname.trim(), picture.presentOrNull())
                    .takeIf { it.key.isNotEmpty() && (it.nickname.isNotEmpty() || it.picture != null) }
        }
    }

    private val rowByKey: Map<Pair<Kind, String>, Row> = rows.filter { it.key.isNotEmpty() }.associateBy { it.kind to it.key }

    fun nicknameFor(login: String): String? = row(Kind.PERSON, login)?.nickname.presentOrNull()

    fun nicknameForAgent(id: String): String? = row(Kind.AGENT, id)?.nickname.presentOrNull()

    fun pictureFor(key: FaceKey): String? = when (key) {
        FaceKey.User -> null
        is FaceKey.Agent -> row(Kind.AGENT, key.id)?.picture.presentOrNull()
        is FaceKey.GitHub -> row(Kind.PERSON, key.login)?.picture.presentOrNull()
    }

    fun displayNameOf(author: Author): String = when (author) {
        is Author.User -> author.displayName
        is Author.Agent -> nicknameForAgent(author.receiptKey) ?: author.displayName
    }

    private fun row(kind: Kind, identity: String): Row? = rowByKey[kind to kind.keyOf(identity)]

    companion object {
        val NONE = People(emptyList())

        fun duplicate(rows: List<Row>): Row? {
            val seen = mutableSetOf<Pair<Kind, String>>()
            return rows.filter { it.key.isNotEmpty() }.firstOrNull { !seen.add(it.kind to it.key) }
        }

        fun unreferencedPictures(stored: Collection<String>, rows: List<Row>, yours: String): List<String> {
            val referenced = (rows.mapNotNull { it.picture } + yours).map(String::trim).toSet()
            return stored.filterNot { it in referenced }
        }

        fun migrated(legacy: Map<String, String>, rows: List<Row>): List<Row> {
            val listed = rows.filter { it.kind == Kind.PERSON }.map { it.key }.toMutableSet()
            val carried = legacy
                .mapNotNull { (login, nickname) -> Row.normalizedOrNull(login, Kind.PERSON, nickname, picture = null) }
                .filter { listed.add(it.key) }
            return rows + carried
        }
    }
}

private fun String?.presentOrNull(): String? = this?.trim()?.ifEmpty { null }
