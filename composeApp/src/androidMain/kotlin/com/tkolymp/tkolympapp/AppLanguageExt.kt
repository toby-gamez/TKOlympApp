package com.tkolymp.tkolympapp

import com.tkolymp.shared.language.AppLanguage
import java.util.Locale

fun AppLanguage.toLocale(): Locale = when (this) {
    AppLanguage.CS -> Locale.forLanguageTag("cs")
    AppLanguage.DE -> Locale.forLanguageTag("de")
    AppLanguage.SK -> Locale.forLanguageTag("sk")
    AppLanguage.SL -> Locale.forLanguageTag("sl")
    AppLanguage.UA -> Locale.forLanguageTag("uk") // Ukrainian BCP-47 is "uk"
    AppLanguage.VI -> Locale.forLanguageTag("vi")
    AppLanguage.EN -> Locale.ENGLISH
    AppLanguage.BRAINROT -> Locale.ENGLISH
}
