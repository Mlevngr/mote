package com.mlevngr.inknote.appearance

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

/** Recreates an existing screen when returning from a changed skin setting. */
abstract class SkinnableActivity : AppCompatActivity() {
    private var skinRevision = ""

    override fun attachBaseContext(newBase: Context) {
        skinRevision = SkinPreferences(newBase).revision
        super.attachBaseContext(SkinTheme.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SkinPreferences.palette(this)?.let { palette ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = SkinColor.onColor(palette.background) == 0xFF000000.toInt()
                isAppearanceLightNavigationBars = isAppearanceLightStatusBars
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (skinRevision != SkinPreferences(this).revision) {
            skinRevision = SkinPreferences(this).revision
            recreate()
        }
    }
}
