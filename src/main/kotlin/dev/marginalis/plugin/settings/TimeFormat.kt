package dev.marginalis.plugin.settings

enum class TimeFormat(val stored: String, val label: String) {
    AUTO("AUTO", "Auto (system)"),
    TWELVE_HOUR("12", "12-hour"),
    TWENTY_FOUR_HOUR("24", "24-hour");

    companion object {
        fun fromStored(stored: String): TimeFormat = entries.firstOrNull { it.stored == stored } ?: AUTO
    }
}
