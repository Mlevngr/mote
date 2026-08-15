package com.mlevngr.inknote.library

enum class FolderColor(val id: String, val light: Int, val dark: Int) {
    Blue("blue", 0xFF3478F6.toInt(), 0xFF689EFF.toInt()),
    Purple("purple", 0xFF8957E1.toInt(), 0xFFB98EFF.toInt()),
    Pink("pink", 0xFFDD4F89.toInt(), 0xFFFF82B2.toInt()),
    Orange("orange", 0xFFE57E2D.toInt(), 0xFFFFA954.toInt()),
    Green("green", 0xFF2F8F58.toInt(), 0xFF5BC37E.toInt()),
    Gray("gray", 0xFF656E7B.toInt(), 0xFFA7B0BE.toInt());

    companion object {
        fun fromId(id: String?): FolderColor = entries.firstOrNull { it.id == id } ?: Blue
    }
}
