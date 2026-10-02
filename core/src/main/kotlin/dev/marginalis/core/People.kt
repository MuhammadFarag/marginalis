package dev.marginalis.core

class People(nicknames: Map<String, String>) {

    private val byLogin: Map<String, String> = nicknames
        .mapKeys { (login, _) -> normalized(login) }
        .mapValues { (_, nickname) -> nickname.trim() }
        .filter { (login, nickname) -> login.isNotEmpty() && nickname.isNotEmpty() }

    fun nicknameFor(login: String): String? = byLogin[normalized(login)]

    companion object {
        val NONE = People(emptyMap())

        fun duplicateLogin(logins: List<String>): String? =
            logins.map(::normalized).filter { it.isNotEmpty() }.groupingBy { it }.eachCount().entries
                .firstOrNull { it.value > 1 }?.key

        private fun normalized(login: String): String = login.trim().removePrefix("@").lowercase()
    }
}
