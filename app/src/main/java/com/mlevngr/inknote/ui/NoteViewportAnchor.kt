package com.mlevngr.inknote.ui

/** A local viewport anchor, not an adapter stable ID or a document identity. */
data class NoteViewportAnchor(val line: Int, val ordinal: Int, val offset: Int) {
    companion object {
        /** Map a line through one contiguous replacement; keep unchanged prefix/suffix exact. */
        fun mapLine(before: List<String>, after: List<String>, line: Int): Int {
            if (after.isEmpty()) return 0
            var prefix = 0
            while (prefix < minOf(before.size, after.size) && before[prefix] == after[prefix]) prefix++
            var suffix = 0
            while (suffix < minOf(before.size, after.size) - prefix &&
                before[before.lastIndex - suffix] == after[after.lastIndex - suffix]
            ) suffix++
            return when {
                line < prefix -> line
                line >= before.size - suffix -> line + after.size - before.size
                else -> minOf(line, (after.size - suffix - 1).coerceAtLeast(prefix))
            }.coerceIn(0, after.lastIndex)
        }
    }
}
