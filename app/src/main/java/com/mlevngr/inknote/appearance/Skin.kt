package com.mlevngr.inknote.appearance

/** Portable, opaque sRGB colors. The skin contains data only, never executable code. */
data class Skin(val name: String, val light: SkinPalette, val dark: SkinPalette) {
    init { require(name.isNotBlank() && name.length <= 64) { "皮肤名称须为 1–64 个字符" } }

    fun palette(night: Boolean): SkinPalette = if (night) dark else light

    companion object {
        val Default = Skin(
            "清晰彩色标题",
            SkinPalette.fromHex(listOf(
                "#FFFFFF", "#F2F5FA", "#202124", "#5F6368", "#1A73E8", "#DADCE0", "#B3261E",
                "#1A73E8", "#7C3AED", "#087F8C", "#B35C00", "#B3261E", "#2E7D32"
            )),
            SkinPalette.fromHex(listOf(
                "#000000", "#18191B", "#F1F3F4", "#BDC1C6", "#8AB4F8", "#5F6368", "#FFB4AB",
                "#8AB4F8", "#C4B5FD", "#5EEAD4", "#FDBA74", "#FDA4AF", "#A7F3D0"
            ))
        )
    }
}

data class SkinPalette(
    val background: Int,
    val surface: Int,
    val text: Int,
    val secondaryText: Int,
    val accent: Int,
    val outline: Int,
    val error: Int,
    val headings: List<Int>
) {
    init {
        require(headings.size == 6) { "必须提供 H1–H6 六种标题颜色" }
        require(values.all { it ushr 24 == 255 }) { "皮肤颜色必须不透明" }
    }

    val values: List<Int> get() = listOf(background, surface, text, secondaryText, accent, outline, error) + headings
    fun heading(level: Int): Int = headings[(level - 1).also { require(it in 0..5) }]

    companion object {
        val keys = listOf("background", "surface", "text", "secondaryText", "accent", "outline", "error")
        fun fromHex(values: List<String>): SkinPalette {
            require(values.size == 13) { "需要 7 种界面颜色和 6 种标题颜色" }
            val colors = values.map(SkinColor::parse)
            return SkinPalette(colors[0], colors[1], colors[2], colors[3], colors[4], colors[5], colors[6], colors.drop(7))
        }
    }
}

object SkinColor {
    fun parse(value: String): Int {
        require(value.matches(Regex("#[0-9a-fA-F]{6}"))) { "颜色格式须为 #RRGGBB，例如 #1A73E8" }
        return (0xFF000000L or value.drop(1).toLong(16)).toInt()
    }

    fun hex(color: Int): String = "#%06X".format(java.util.Locale.ROOT, color and 0xFFFFFF)

    fun luminance(color: Int): Double {
        fun component(shift: Int): Double {
            val channel = ((color ushr shift) and 255) / 255.0
            return if (channel <= 0.04045) channel / 12.92 else Math.pow((channel + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * component(16) + 0.7152 * component(8) + 0.0722 * component(0)
    }

    fun onColor(color: Int): Int = if (luminance(color) > 0.179) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
}
