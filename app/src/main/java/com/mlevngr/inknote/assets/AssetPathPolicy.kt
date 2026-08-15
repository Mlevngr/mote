package com.mlevngr.inknote.assets

import java.io.File

/** Resolves only note-local `assets/` paths and rejects traversal or absolute paths. */
object AssetPathPolicy {
    fun resolve(noteRoot: File, markdownPath: String): File? {
        if (!markdownPath.startsWith("assets/") || File(markdownPath).isAbsolute) return null
        val assetRoot = File(noteRoot, "assets").canonicalFile
        val candidate = File(noteRoot, markdownPath).canonicalFile
        return candidate.takeIf {
            it.path == assetRoot.path || it.path.startsWith(assetRoot.path + File.separator)
        }
    }
}
