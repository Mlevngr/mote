package com.mlevngr.mote.plugin.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AiPluginConfiguration(
    val internalEndpoint: String,
    val externalEndpoint: String,
    val model: String,
    val apiKey: String
) {
    val endpoints: List<String> get() = listOf(internalEndpoint, externalEndpoint)
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
}

class AiPluginSettings(context: Context) {
    private val preferences = context.getSharedPreferences("ai_plugin_settings", Context.MODE_PRIVATE)

    fun load(): AiPluginConfiguration = AiPluginConfiguration(
        internalEndpoint = preferences.getString(KEY_INTERNAL, "").orEmpty(),
        externalEndpoint = preferences.getString(KEY_EXTERNAL, "").orEmpty(),
        model = preferences.getString(KEY_MODEL, "").orEmpty(),
        apiKey = decrypt(
            preferences.getString(KEY_API_KEY, null),
            preferences.getString(KEY_API_IV, null)
        )
    )

    fun save(configuration: AiPluginConfiguration) {
        val encrypted = encrypt(configuration.apiKey)
        preferences.edit {
            putString(KEY_INTERNAL, configuration.internalEndpoint.trim())
            putString(KEY_EXTERNAL, configuration.externalEndpoint.trim())
            putString(KEY_MODEL, configuration.model.trim())
            putString(KEY_API_KEY, encrypted?.first)
            putString(KEY_API_IV, encrypted?.second)
        }
    }

    private fun encrypt(value: String): Pair<String, String>? {
        if (value.isEmpty()) return "" to ""
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            Base64.encodeToString(cipher.doFinal(value.toByteArray()), Base64.NO_WRAP) to
                Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        }.getOrNull()
    }

    private fun decrypt(value: String?, iv: String?): String {
        if (value.isNullOrEmpty() || iv.isNullOrEmpty()) return ""
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(value, Base64.NO_WRAP)))
        }.getOrDefault("")
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
        }.generateKey()
    }

    private companion object {
        const val KEY_INTERNAL = "internal_endpoint"
        const val KEY_EXTERNAL = "external_endpoint"
        const val KEY_MODEL = "model"
        const val KEY_API_KEY = "api_key"
        const val KEY_API_IV = "api_iv"
        const val KEY_ALIAS = "mote_ai_plugin_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
