package com.mlevngr.inknote.markdown

data class MarkdownTaskLine(
    val isCompleted: Boolean,
    val content: String
) {
    companion object {
        private val pattern = Regex(
            "^(\\s*(?:[-+*]|\\d+[.)])\\s+\\[)([ xX])](?:[ \\t]+(.*))?$"
        )

        fun parse(source: String): MarkdownTaskLine? {
            val match = pattern.matchEntire(source) ?: return null
            return MarkdownTaskLine(
                isCompleted = !match.groupValues[2].equals(" "),
                content = match.groupValues[3]
            )
        }

        fun toggle(source: String): String? {
            val match = pattern.matchEntire(source) ?: return null
            val marker = match.groups[2] ?: return null
            val replacement = if (marker.value == " ") "x" else " "
            return source.replaceRange(marker.range, replacement)
        }
    }
}
