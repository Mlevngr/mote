package com.mlevngr.inknote.library

enum class FolderColor(val id: String, val light: Int, val dark: Int) {
    Blue("blue", 0xFF3478F6.toInt(), 0xFF689EFF.toInt()),
    Indigo("indigo", 0xFF4F5BD5.toInt(), 0xFF9FA8FF.toInt()),
    Purple("purple", 0xFF8957E1.toInt(), 0xFFB98EFF.toInt()),
    Pink("pink", 0xFFDD4F89.toInt(), 0xFFFF82B2.toInt()),
    Red("red", 0xFFD93025.toInt(), 0xFFFF8A80.toInt()),
    Orange("orange", 0xFFE57E2D.toInt(), 0xFFFFA954.toInt()),
    Amber("amber", 0xFFF9AB00.toInt(), 0xFFFFCA55.toInt()),
    Green("green", 0xFF2F8F58.toInt(), 0xFF5BC37E.toInt()),
    Teal("teal", 0xFF00897B.toInt(), 0xFF64D8CB.toInt()),
    Cyan("cyan", 0xFF039BE5.toInt(), 0xFF63C7F5.toInt()),
    Brown("brown", 0xFF795548.toInt(), 0xFFBCAAA4.toInt()),
    Gray("gray", 0xFF656E7B.toInt(), 0xFFA7B0BE.toInt());

    companion object {
        fun fromId(id: String?): FolderColor = entries.firstOrNull { it.id == id } ?: Blue
    }
}
