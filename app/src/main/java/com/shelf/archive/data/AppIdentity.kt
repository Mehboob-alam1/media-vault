package com.shelf.archive.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

object AppIdentity {
    fun sha1(context: Context): String {
        return try {
            val signature = signatures(context).firstOrNull() ?: return "Unavailable"
            val digest = MessageDigest.getInstance("SHA-1").digest(signature.toByteArray())
            digest.joinToString(":") { byte -> "%02X".format(byte) }
        } catch (_: Exception) {
            "Unavailable"
        }
    }

    private fun signatures(context: Context): List<android.content.pm.Signature> {
        val packageManager = context.packageManager
        val packageName = context.packageName
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signing = info.signingInfo ?: return emptyList()
            val signers = if (signing.hasMultipleSigners()) {
                signing.apkContentsSigners
            } else {
                signing.signingCertificateHistory
            }
            signers?.toList().orEmpty()
        } else {
            @Suppress("DEPRECATION")
            val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            info.signatures?.toList().orEmpty()
        }
    }
}
