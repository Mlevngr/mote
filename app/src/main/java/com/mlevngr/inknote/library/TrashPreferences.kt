package com.mlevngr.inknote.library

import android.content.Context
import androidx.core.content.edit

enum class TrashRetention(val id: String, val days: Int) {
    SevenDays("7_days", 7),
    ThirtyDays("30_days", 30),
    NinetyDays("90_days", 90);

    val durationMillis: Long get() = days * 24L * 60L * 60L * 1_000L

    companion object {
        fun fromId(id: String?): TrashRetention = entries.firstOrNull { it.id == id } ?: ThirtyDays
    }
}

class TrashPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    var retention: TrashRetention
        get() = TrashRetention.fromId(preferences.getString(KEY_RETENTION, null))
        set(value) { preferences.edit { putString(KEY_RETENTION, value.id) } }

    private companion object {
        const val PREFERENCES = "trash"
        const val KEY_RETENTION = "retention"
    }
}
