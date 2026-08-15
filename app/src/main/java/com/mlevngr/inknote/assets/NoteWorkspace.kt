package com.mlevngr.inknote.assets

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID

class NoteWorkspace(context: Context, noteId: String = "welcome") {
    val root = File(context.filesDir, "notes/$noteId").also(File::mkdirs)
    private val assets = File(root, "assets").also(File::mkdirs)
    private val markdownFile = File(root, "note.md")

    fun load(defaultValue: String): String {
        if (!markdownFile.exists()) save(defaultValue)
        return markdownFile.readText()
    }

    fun save(markdown: String) {
        val temporary = File(root, "note.md.tmp")
        temporary.writeText(markdown)
        if (!temporary.renameTo(markdownFile)) {
            markdownFile.writeText(markdown)
            temporary.delete()
        }
    }

    fun resolveAsset(relativePath: String): File? =
        AssetPathPolicy.resolve(root, relativePath)?.takeIf(File::isFile)

    fun import(resolver: ContentResolver, uri: Uri, kind: ImportedAsset.Kind): ImportedAsset {
        val displayName = queryDisplayName(resolver, uri) ?: when (kind) {
            ImportedAsset.Kind.Image -> "image"
            ImportedAsset.Kind.Pdf -> "document.pdf"
        }
        val extension = extensionFor(resolver.getType(uri), displayName, kind)
        val fileName = "${UUID.randomUUID()}.$extension"
        val destination = File(assets, fileName)
        val temporary = File(assets, "$fileName.tmp")
        resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open selected file" }
            temporary.outputStream().use { output -> input.copyTo(output) }
        }
        check(temporary.renameTo(destination)) { "Cannot store selected file" }
        return ImportedAsset("assets/$fileName", displayName, kind)
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) null else cursor.getString(0)
        }

    private fun extensionFor(
        mimeType: String?,
        displayName: String,
        kind: ImportedAsset.Kind
    ): String {
        if (kind == ImportedAsset.Kind.Pdf) return "pdf"
        return when (mimeType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/heif", "image/heic" -> "heic"
            else -> displayName.substringAfterLast('.', "jpg")
                .lowercase()
                .takeIf { it.matches(Regex("[a-z0-9]{1,5}")) }
                ?: "jpg"
        }
    }
}
