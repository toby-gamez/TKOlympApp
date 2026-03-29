package com.tkolymp.shared.language

data class AuthStrings(
    val emailOrUsername: String,
    val password: String,
    val newPassword: String,
    val confirmPassword: String,
    val passwordTooShort: String,
    val login: String,
    val changePassword: String,
    val forgotPassword: String,
    val loginSubtitle: String,
)
