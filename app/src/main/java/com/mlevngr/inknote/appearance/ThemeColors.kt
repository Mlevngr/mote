package com.mlevngr.inknote.appearance

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes

object ThemeColors {
    fun resolve(context: Context, @AttrRes attribute: Int): Int {
        val value = TypedValue()
        check(context.theme.resolveAttribute(attribute, value, true)) {
            "Theme attribute $attribute is not defined"
        }
        return if (value.resourceId != 0) context.getColor(value.resourceId) else value.data
    }
}
