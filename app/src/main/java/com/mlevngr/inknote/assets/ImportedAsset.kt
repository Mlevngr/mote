package com.mlevngr.inknote.assets

data class ImportedAsset(
    val relativePath: String,
    val displayName: String,
    val kind: Kind
) {
    enum class Kind { Image, Pdf }

    fun markdown(): String {
        val label = displayName
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace('[', '(')
            .replace(']', ')')
        return when (kind) {
            Kind.Image -> "![$label]($relativePath)"
            Kind.Pdf -> "[$label]($relativePath)"
        }
    }
}
