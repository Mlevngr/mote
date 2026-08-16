package com.mlevngr.inknote.appearance

import com.mlevngr.inknote.library.FolderColor

data class ThemePreviewColors(
    val background: Int,
    val surface: Int,
    val accent: Int
)

/** Theme-owned color mapping for previews and semantic folder colors. */
object ThemePalette {
    fun preview(theme: AppTheme, night: Boolean): ThemePreviewColors = when (theme) {
        AppTheme.InkNote -> if (night) {
            ThemePreviewColors(0xFF000000.toInt(), 0xFF18191B.toInt(), 0xFF8AB4F8.toInt())
        } else {
            ThemePreviewColors(0xFFFFFFFF.toInt(), 0xFFF2F5FA.toInt(), 0xFF1A73E8.toInt())
        }
        AppTheme.Catppuccin -> if (night) {
            ThemePreviewColors(0xFF1E1E2E.toInt(), 0xFF313244.toInt(), 0xFFCBA6F7.toInt())
        } else {
            ThemePreviewColors(0xFFEFF1F5.toInt(), 0xFFE6E9EF.toInt(), 0xFF8839EF.toInt())
        }
        AppTheme.TokyoNight -> if (night) {
            ThemePreviewColors(0xFF1A1B26.toInt(), 0xFF202330.toInt(), 0xFF7AA2F7.toInt())
        } else {
            ThemePreviewColors(0xFFE6E7ED.toInt(), 0xFFD5D6DB.toInt(), 0xFF2959AA.toInt())
        }
        AppTheme.Minimal -> if (night) {
            ThemePreviewColors(0xFF000000.toInt(), 0xFF161616.toInt(), 0xFFA78BFA.toInt())
        } else {
            ThemePreviewColors(0xFFFAFAFA.toInt(), 0xFFFFFFFF.toInt(), 0xFF7C3AED.toInt())
        }
    }

    fun folderColor(theme: AppTheme, color: FolderColor, night: Boolean): Int = when (theme) {
        AppTheme.InkNote -> if (night) color.dark else color.light
        AppTheme.Catppuccin -> catppuccinFolderColor(color, night)
        AppTheme.TokyoNight -> tokyoNightFolderColor(color, night)
        AppTheme.Minimal -> minimalFolderColor(color, night)
    }

    private fun catppuccinFolderColor(color: FolderColor, night: Boolean): Int {
        return (if (night) CATPPUCCIN_DARK else CATPPUCCIN_LIGHT)[color.ordinal]
    }

    private fun tokyoNightFolderColor(color: FolderColor, night: Boolean): Int {
        return (if (night) TOKYO_NIGHT_DARK else TOKYO_NIGHT_LIGHT)[color.ordinal]
    }

    private fun minimalFolderColor(color: FolderColor, night: Boolean): Int {
        return (if (night) MINIMAL_DARK else MINIMAL_LIGHT)[color.ordinal]
    }

    private val CATPPUCCIN_LIGHT = intArrayOf(
        0xFF1E66F5.toInt(), 0xFF7287FD.toInt(), 0xFF8839EF.toInt(),
        0xFFEA76CB.toInt(), 0xFFD20F39.toInt(), 0xFFFE640B.toInt(),
        0xFFDF8E1D.toInt(), 0xFF40A02B.toInt(), 0xFF179299.toInt(),
        0xFF04A5E5.toInt(), 0xFFDC8A78.toInt(), 0xFF7C7F93.toInt()
    )
    private val CATPPUCCIN_DARK = intArrayOf(
        0xFF89B4FA.toInt(), 0xFFB4BEFE.toInt(), 0xFFCBA6F7.toInt(),
        0xFFF5C2E7.toInt(), 0xFFF38BA8.toInt(), 0xFFFAB387.toInt(),
        0xFFF9E2AF.toInt(), 0xFFA6E3A1.toInt(), 0xFF94E2D5.toInt(),
        0xFF89DCEB.toInt(), 0xFFF5E0DC.toInt(), 0xFF9399B2.toInt()
    )
    private val TOKYO_NIGHT_LIGHT = intArrayOf(
        0xFF2959AA.toInt(), 0xFF34548A.toInt(), 0xFF5A3E8E.toInt(),
        0xFFA64C7E.toInt(), 0xFF8C4351.toInt(), 0xFF965027.toInt(),
        0xFF8F5E15.toInt(), 0xFF385F0D.toInt(), 0xFF33635C.toInt(),
        0xFF0F4B6E.toInt(), 0xFF634F30.toInt(), 0xFF6C6E75.toInt()
    )
    private val TOKYO_NIGHT_DARK = intArrayOf(
        0xFF7AA2F7.toInt(), 0xFF3D59A1.toInt(), 0xFFBB9AF7.toInt(),
        0xFFF7768E.toInt(), 0xFFDB4B4B.toInt(), 0xFFFF9E64.toInt(),
        0xFFE0AF68.toInt(), 0xFF9ECE6A.toInt(), 0xFF73DACA.toInt(),
        0xFF7DCFFF.toInt(), 0xFFCFC9C2.toInt(), 0xFF565F89.toInt()
    )
    private val MINIMAL_LIGHT = intArrayOf(
        0xFF4F6FAE.toInt(), 0xFF6269A8.toInt(), 0xFF7C5AA6.toInt(),
        0xFFA85F7F.toInt(), 0xFFAA5555.toInt(), 0xFFAA6A45.toInt(),
        0xFFA48645.toInt(), 0xFF5F835F.toInt(), 0xFF4E817B.toInt(),
        0xFF4F829A.toInt(), 0xFF806A60.toInt(), 0xFF747474.toInt()
    )
    private val MINIMAL_DARK = intArrayOf(
        0xFF83A7E8.toInt(), 0xFF999FDC.toInt(), 0xFFA78BFA.toInt(),
        0xFFD68AAA.toInt(), 0xFFD98484.toInt(), 0xFFD99A73.toInt(),
        0xFFD5B56E.toInt(), 0xFF8EB58E.toInt(), 0xFF7EB5AD.toInt(),
        0xFF7FB2C9.toInt(), 0xFFB39A8E.toInt(), 0xFF9B9B9B.toInt()
    )
}
