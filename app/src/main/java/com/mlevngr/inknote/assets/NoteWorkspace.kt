package com.mlevngr.inknote.assets

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.mlevngr.inknote.library.NoteLibrary
import com.mlevngr.inknote.library.NoteLibrary.FolderLocation
import java.io.File
import java.util.UUID

class NoteWorkspace(context: Context, folderLocation: FolderLocation, noteName: String) {
    val root = NoteLibrary(context).findNoteDirectory(folderLocation, noteName)
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

    fun import(resolver: ContentResolver, uri: Uri): ImportedAsset {
        val displayName = queryDisplayName(resolver, uri) ?: "attachment"
        val mimeType = resolver.getType(uri)
        val kind = detectKind(mimeType, displayName)
        return import(resolver, uri, kind, displayName, mimeType)
    }

    private fun import(
        resolver: ContentResolver,
        uri: Uri,
        kind: ImportedAsset.Kind,
        displayName: String,
        mimeType: String?
    ): ImportedAsset {
        val extension = extensionFor(mimeType, displayName, kind)
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

    private fun detectKind(mimeType: String?, displayName: String): ImportedAsset.Kind = when {
        mimeType?.startsWith("image/") == true -> ImportedAsset.Kind.Image
        mimeType == "application/pdf" || displayName.endsWith(".pdf", ignoreCase = true) ->
            ImportedAsset.Kind.Pdf
        else -> ImportedAsset.Kind.Attachment
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
        val fallback = if (kind == ImportedAsset.Kind.Image) "jpg" else "bin"
        return when (mimeType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/heif", "image/heic" -> "heic"
            else -> displayName.substringAfterLast('.', fallback)
                .lowercase()
                .takeIf { it.matches(Regex("[a-z0-9]{1,10}")) }
                ?: fallback
        }
    }
}
