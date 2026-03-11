package com.tkolymp.shared.language

import com.tkolymp.shared.language.translations.StringsBrainrot
import com.tkolymp.shared.language.translations.StringsCs
import com.tkolymp.shared.language.translations.StringsDe
import com.tkolymp.shared.language.translations.StringsEn
import com.tkolymp.shared.language.translations.StringsSk
import com.tkolymp.shared.language.translations.StringsSl
import com.tkolymp.shared.language.translations.StringsUa
import com.tkolymp.shared.language.translations.StringsVi

/**
 * Central access point for translated strings.
 *
 * Usage:
 *   AppStrings.current.overview
 *
 * Change language at runtime:
 *   AppStrings.setLanguage(AppLanguage.DE)
 */
object AppStrings {
    private var _current: Strings = StringsCs
    private var _currentLanguage: AppLanguage = AppLanguage.CS
    val current: Strings get() = _current
    val currentLanguage: AppLanguage get() = _currentLanguage

    fun setLanguage(language: AppLanguage) {
        _current = forLanguage(language)
        _currentLanguage = language
    }

    fun forLanguage(language: AppLanguage): Strings = when (language) {
        AppLanguage.CS -> StringsCs
        AppLanguage.DE -> StringsDe
        AppLanguage.SK -> StringsSk
        AppLanguage.SL -> StringsSl
        AppLanguage.UA -> StringsUa
        AppLanguage.VI -> StringsVi
        AppLanguage.EN -> StringsEn
        AppLanguage.BRAINROT -> StringsBrainrot
    }
}
