package com.mlevngr.inknote.assets

data class ImportedAsset(
    val relativePath: String,
    val displayName: String,
    val kind: Kind,
    val instanceId: String? = null
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
        val metadata = if (kind == Kind.Pdf && instanceId != null) {
            "|mote-id:$instanceId"
        } else ""
        return "![[asset:$relativePath|$label$metadata]]"
    }
}
