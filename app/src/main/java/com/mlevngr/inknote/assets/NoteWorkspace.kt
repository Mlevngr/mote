package com.mlevngr.inknote.assets

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.mlevngr.inknote.library.NoteLibrary
import com.mlevngr.inknote.library.NoteLibrary.FolderLocation
import com.mlevngr.inknote.markdown.LegacyNoteBodyMigration
import com.mlevngr.inknote.storage.AtomicFileWriter
import com.mlevngr.inknote.storage.VaultOperationLock
import java.io.File
import java.util.UUID

class NoteWorkspace(context: Context, folderLocation: FolderLocation, private val noteName: String) {
    val root = NoteLibrary(context).findNoteDirectory(folderLocation, noteName)
    private val assets = File(root, "assets").also(File::mkdirs)
    private val markdownFile = File(root, "note.md")
    private val bodySeparationMarker = File(root, NoteLibrary.BODY_SEPARATION_MARKER)

    fun load(defaultValue: String): String = VaultOperationLock.withLock {
        if (!markdownFile.exists()) save(defaultValue)
        val markdown = markdownFile.readText()
        if (bodySeparationMarker.exists()) return@withLock markdown

        val separated = LegacyNoteBodyMigration.separateTitle(markdown, noteName)
        if (separated != markdown) save(separated)
        AtomicFileWriter.writeText(bodySeparationMarker, "")
        separated
    }

    fun save(markdown: String) {
        AtomicFileWriter.writeText(markdownFile, markdown)
    }

    fun resolveAsset(relativePath: String): File? =
        AssetPathPolicy.resolve(root, relativePath)?.takeIf(File::isFile)

    fun deleteAssetIfUnreferenced(file: File, remainingMarkdown: String): Boolean =
        VaultOperationLock.withLock {
            val canonicalFile = file.canonicalFile
            val canonicalAssets = assets.canonicalFile
            val assetPrefix = "${canonicalAssets.path}${File.separator}"
            require(canonicalFile.path.startsWith(assetPrefix)) { "Asset is outside this note" }
            val relativePath = canonicalFile.relativeTo(root.canonicalFile).invariantSeparatorsPath
            if (relativePath in remainingMarkdown) return@withLock false
            !canonicalFile.exists() || canonicalFile.delete()
        }

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
        AtomicFileWriter.write(destination) { output ->
            resolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Cannot open selected file" }
                input.copyTo(output)
            }
        }
        return ImportedAsset(
            relativePath = "assets/$fileName",
            displayName = displayName,
            kind = kind,
            instanceId = if (kind == ImportedAsset.Kind.Pdf) UUID.randomUUID().toString() else null
        )
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
