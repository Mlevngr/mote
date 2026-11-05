package com.mlevngr.inknote.appearance

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit

class SkinPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("skin", Context.MODE_PRIVATE)
    val enabled: Boolean get() = preferences.getBoolean("enabled", false)
    val revision: String get() = if (enabled) preferences.getString("json", "").orEmpty() else ""
    fun saved(): Skin = preferences.getString("json", null)
        ?.let { runCatching { SkinJson.decode(it) }.getOrNull() } ?: Skin.Default
    fun active(): Skin? = if (enabled) saved() else null
    fun apply(skin: Skin) {
        preferences.edit { putString("json", SkinJson.encode(skin)); putBoolean("enabled", true) }
    }
    fun disable() { preferences.edit { putBoolean("enabled", false) } }

    companion object {
        fun palette(context: Context): SkinPalette? = SkinPreferences(context).active()?.palette(
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        )
    }
}
