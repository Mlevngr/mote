package com.mlevngr.inknote.plugins

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.edit
import com.mlevngr.mote.plugin.api.PluginDescriptor
import java.security.MessageDigest

class PluginApprovalStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("plugin_approvals", Context.MODE_PRIVATE)

    fun isApproved(packageName: String, descriptor: PluginDescriptor): Boolean =
        preferences.getBoolean(approvalKey(packageName, descriptor), false)

    fun approve(packageName: String, descriptor: PluginDescriptor) {
        preferences.edit { putBoolean(approvalKey(packageName, descriptor), true) }
    }

    fun revoke(packageName: String, descriptor: PluginDescriptor) {
        preferences.edit { remove(approvalKey(packageName, descriptor)) }
    }

    private fun approvalKey(packageName: String, descriptor: PluginDescriptor): String =
        descriptor.approvalKey("$packageName@${signingDigest(packageName)}")

    private fun signingDigest(packageName: String): String = runCatching {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            appContext.packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            appContext.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }
        val signatures = info.signingInfo?.apkContentsSigners.orEmpty()
        val digest = MessageDigest.getInstance("SHA-256")
        signatures.map { signature ->
            digest.digest(signature.toByteArray()).joinToString("") { byte -> "%02x".format(byte) }
        }.sorted().joinToString(",").ifBlank { "unsigned" }
    }.getOrDefault("unavailable")
}
