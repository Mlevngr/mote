package com.mlevngr.inknote.assets

data class ImportedAsset(
    val relativePath: String,
    val displayName: String,
    val kind: Kind
) {
    enum class Kind { Image, Pdf, Attachment }

    /** InkNote embed syntax is deliberately not a clickable Markdown hyperlink. */
    fun markdown(): String {
        val label = displayName
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace('|', ' ')
            .replace('[', '(')
            .replace(']', ')')
        return "![[asset:$relativePath|$label]]"
    }
}
