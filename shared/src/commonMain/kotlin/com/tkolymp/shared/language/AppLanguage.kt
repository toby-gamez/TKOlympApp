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
        fun fromCode(code: String): AppLanguage {
            // "uk" is the standard ISO 639-1 code for Ukrainian; map to our internal "ua"
            val normalized = if (code == "uk") "ua" else code
            return entries.find { it.code == normalized } ?: CS
        }
    }
}
