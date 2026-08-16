package com.mlevngr.inknote.sync

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

class WebDavPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val secrets = WebDavSecretCipher()

    fun load(): WebDavConfig = WebDavConfig(
        internalUrl = preferences.getString(KEY_INTERNAL_URL, "").orEmpty(),
        externalUrl = preferences.getString(KEY_EXTERNAL_URL, "").orEmpty(),
        remoteFolder = preferences.getString(KEY_REMOTE_FOLDER, WebDavConfig.DEFAULT_REMOTE_FOLDER)
            .orEmpty(),
        username = preferences.getString(KEY_USERNAME, "").orEmpty(),
        password = decryptPassword()
    )

    fun save(config: WebDavConfig): WebDavConfig {
        val validated = config.validated()
        val encrypted = secrets.encrypt(validated.password)
        preferences.edit(commit = true) {
            putString(KEY_INTERNAL_URL, validated.internalUrl)
            putString(KEY_EXTERNAL_URL, validated.externalUrl)
            putString(KEY_REMOTE_FOLDER, validated.remoteFolder)
            putString(KEY_USERNAME, validated.username)
            putString(KEY_PASSWORD_IV, encrypted.iv)
            putString(KEY_PASSWORD_CIPHERTEXT, encrypted.ciphertext)
        }
        return validated
    }

    val configured: Boolean get() = load().run {
        internalUrl.isNotBlank() || externalUrl.isNotBlank()
    }

    private fun decryptPassword(): String {
        val iv = preferences.getString(KEY_PASSWORD_IV, null) ?: return ""
        val ciphertext = preferences.getString(KEY_PASSWORD_CIPHERTEXT, null) ?: return ""
        return runCatching { secrets.decrypt(EncryptedSecret(iv, ciphertext)) }.getOrDefault("")
    }

    private companion object {
        const val PREFERENCES = "webdav"
        const val KEY_INTERNAL_URL = "internal_url"
        const val KEY_EXTERNAL_URL = "external_url"
        const val KEY_REMOTE_FOLDER = "remote_folder"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD_IV = "password_iv"
        const val KEY_PASSWORD_CIPHERTEXT = "password_ciphertext"
    }
}

private data class EncryptedSecret(val iv: String, val ciphertext: String)

private class WebDavSecretCipher {
    fun encrypt(value: String): EncryptedSecret {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return EncryptedSecret(
            iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            ciphertext = Base64.encodeToString(cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
        )
    }

    fun decrypt(secret: EncryptedSecret): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(128, Base64.decode(secret.iv, Base64.NO_WRAP))
        )
        return cipher.doFinal(Base64.decode(secret.ciphertext, Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "mote.webdav.password.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
