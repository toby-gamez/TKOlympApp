package com.tkolymp.shared.integrity

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.tkolymp.shared.Logger
import java.security.MessageDigest

/**
 * Expected SHA-256 fingerprint of the release signing certificate.
 *
 * Replace this value with the output of:
 *   keytool -list -v -keystore <your-keystore.jks> -storepass <password> | grep "SHA256:"
 *
 * Format: colon-separated uppercase hex pairs, e.g.
 *   "AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67"
 */
private const val EXPECTED_CERT_SHA256 =
    "REPLACE_WITH_YOUR_SHA256_FINGERPRINT"

class IntegrityServiceAndroid(private val context: Context) : IIntegrityService {

    override suspend fun isValid(): Boolean {
        return try {
            val fingerprint = getSigningCertSha256()
            val valid = fingerprint.equals(EXPECTED_CERT_SHA256, ignoreCase = true)
            if (!valid) {
                Logger.d("IntegrityCheck", "Certificate fingerprint mismatch: $fingerprint")
            }
            valid
        } catch (e: Exception) {
            Logger.d("IntegrityCheck", "Integrity check failed with exception: ${e.message}")
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun getSigningCertSha256(): String {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
            info.signingInfo?.apkContentsSigners ?: emptyArray()
        } else {
            val info = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            )
            info.signatures ?: emptyArray()
        }

        if (signatures.isEmpty()) return ""

        val cert = signatures[0].toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(cert)
        return digest.joinToString(":") { "%02X".format(it) }
    }
}
