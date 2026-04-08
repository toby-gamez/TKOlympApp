package com.tkolymp.shared.integrity

/**
 * iOS no-op: OS-level code-signing and App Store review are the enforcement mechanisms.
 */
class IntegrityServiceIos : IIntegrityService {
    override suspend fun isValid(): Boolean = true
}
