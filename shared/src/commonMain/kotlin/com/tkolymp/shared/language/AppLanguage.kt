package com.tkolymp.shared.language

enum class AppLanguage(
    val code: String,
    val nativeName: String,
    val flagEmoji: String,
    val isBrainrot: Boolean = false
) {
    CS("cs", "Čeština", "🇨🇿"),
    DE("de", "Deutsch", "🇩🇪"),
    SK("sk", "Slovenčina", "🇸🇰"),
    SL("sl", "Slovenščina", "🇸🇮"),
    UA("ua", "Українська", "🇺🇦"),
    VI("vi", "Tiếng Việt", "🇻🇳"),
    EN("en", "English", "🇬🇧"),
    BRAINROT("en_au", "Brainrot", "🧠", isBrainrot = true);

    companion object {
        fun fromCode(code: String): AppLanguage =
            entries.find { it.code == code } ?: CS
    }
}
