package com.mlevngr.inknote.appearance

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.loader.ResourcesLoader
import android.content.res.loader.ResourcesProvider
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.appcompat.view.ContextThemeWrapper
import com.mlevngr.inknote.R
import java.io.File

object SkinTheme {
    private class SkinContext(base: Context) : ContextThemeWrapper(base, R.style.Theme_InkNote_Skin)

    fun isApplied(context: Context): Boolean {
        var current = context
        while (current is ContextWrapper) {
            if (current is SkinContext) return true
            current = current.baseContext
        }
        return false
    }

    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT < 30) return base
        val palette = SkinPreferences.palette(base) ?: return base
        return runCatching {
            // A separate Resources instance keeps this Activity's overrides out of other contexts.
            val wrapper = SkinContext(base)
            wrapper.applyOverrideConfiguration(Configuration())
            val colors = mapOf(
                R.color.mote_skin_background to palette.background,
                R.color.mote_skin_surface to palette.surface,
                R.color.mote_skin_text to palette.text,
                R.color.mote_skin_secondary to palette.secondaryText,
                R.color.mote_skin_accent to palette.accent,
                R.color.mote_skin_on_accent to SkinColor.onColor(palette.accent),
                R.color.mote_skin_outline to palette.outline,
                R.color.mote_skin_error to palette.error
            )
            val file = File.createTempFile("skin-", ".arsc", base.cacheDir)
            try {
                file.writeBytes(SkinColorTable.create(
                    base.packageName, colors, colors.keys.associateWith(base.resources::getResourceEntryName)
                ))
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    val loader = ResourcesLoader()
                    loader.addProvider(ResourcesProvider.loadFromTable(descriptor, null))
                    wrapper.resources.addLoaders(loader)
                }
            } finally { file.delete() }
            wrapper
        }.getOrDefault(base)
    }
}
