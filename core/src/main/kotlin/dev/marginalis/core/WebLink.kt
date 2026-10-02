package dev.marginalis.core

object WebLink {

    private val WEB = Regex("(?i)(https?)://[^/?#\\s]+(?:[/?#]\\S*)?")

    fun isBrowsable(url: String): Boolean = WEB.matches(url)

    fun isHttps(url: String): Boolean = WEB.matchEntire(url)?.groupValues?.get(1)?.lowercase() == "https"
}
