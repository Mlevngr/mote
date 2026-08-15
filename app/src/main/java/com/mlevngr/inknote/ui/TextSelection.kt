package com.mlevngr.inknote.ui

internal object TextSelection {
    fun wordAt(value: String, requestedOffset: Int): IntRange? {
        if (value.isEmpty()) return null

        val offset = requestedOffset.coerceIn(0, value.lastIndex)
        if (!value[offset].isWordCharacter()) return offset..offset

        var start = offset
        var endExclusive = offset + 1
        while (start > 0 && value[start - 1].isWordCharacter()) start--
        while (endExclusive < value.length && value[endExclusive].isWordCharacter()) endExclusive++
        return start until endExclusive
    }

    private fun Char.isWordCharacter(): Boolean = isLetterOrDigit() || this == '_'
}
