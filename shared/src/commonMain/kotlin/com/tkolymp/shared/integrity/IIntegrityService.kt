package com.tkolymp.shared.integrity

/**
 * Checks whether the running app binary was signed by the official release key.
 *
 * On Android the check compares the SHA-256 fingerprint of the installed APK's
 * signing certificate against the expected value baked into [EXPECTED_CERT_SHA256].
 *
 * On iOS a no-op implementation is provided (code-signing enforcement is handled
 * by the OS and App Store).
 */
interface IIntegrityService {
    /**
     * Returns `true` when the app passes the integrity check, `false` otherwise.
     * Must be called from a coroutine (may perform I/O on Android).
     */
    suspend fun isValid(): Boolean
}
