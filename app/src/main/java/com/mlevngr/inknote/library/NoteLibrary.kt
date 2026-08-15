package com.mlevngr.inknote.library

import android.content.Context
import java.io.File

class NoteLibrary internal constructor(private val root: File) {
    constructor(context: Context) : this(File(context.filesDir, "notes"))

    init {
        root.mkdirs()
    }

    enum class EntryType { Folder, Note }

    data class Entry(
        val name: String,
        val relativePath: String,
        val type: EntryType,
        val modifiedAt: Long
    )

    fun list(folderPath: String): List<Entry> {
        val folder = requireFolder(folderPath)
        return folder.listFiles().orEmpty()
            .filter { it.isDirectory && !it.name.startsWith('.') }
            .map { directory ->
                val type = if (File(directory, NOTE_FILE).isFile) EntryType.Note
                else EntryType.Folder
                Entry(
                    name = directory.name,
                    relativePath = relativePath(directory),
                    type = type,
                    modifiedAt = if (type == EntryType.Note) {
                        File(directory, NOTE_FILE).lastModified()
                    } else directory.lastModified()
                )
            }
            .sortedWith(compareBy<Entry> { it.type != EntryType.Folder }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    fun createFolder(parentPath: String, requestedName: String): Entry {
        val parent = requireFolder(parentPath)
        val name = normalizedName(requestedName, stripMarkdownExtension = false)
        val folder = uniqueChild(parent, name)
        check(folder.mkdir()) { "无法创建文件夹" }
        return Entry(
            name = folder.name,
            relativePath = relativePath(folder),
            type = EntryType.Folder,
            modifiedAt = folder.lastModified()
        )
    }

    fun createNote(parentPath: String, requestedName: String): Entry {
        val parent = requireFolder(parentPath)
        val name = normalizedName(requestedName, stripMarkdownExtension = true)
        val note = uniqueChild(parent, name)
        check(note.mkdir()) { "无法创建笔记" }
        File(note, ASSETS_DIRECTORY).mkdirs()
        File(note, NOTE_FILE).writeText("# $name\n")
        return Entry(
            name = note.name,
            relativePath = relativePath(note),
            type = EntryType.Note,
            modifiedAt = System.currentTimeMillis()
        )
    }

    fun parentOf(folderPath: String): String? {
        if (folderPath.isBlank()) return null
        return folderPath.substringBeforeLast('/', "")
    }

    private fun requireFolder(folderPath: String): File {
        val folder = NotePathPolicy.resolve(root, folderPath)
            ?: error("无效的文件夹路径")
        require(folder.isDirectory) { "文件夹不存在" }
        require(!File(folder, NOTE_FILE).exists()) { "笔记不能包含子项目" }
        return folder
    }

    private fun uniqueChild(parent: File, baseName: String): File {
        var candidate = File(parent, baseName)
        var suffix = 2
        while (candidate.exists()) {
            candidate = File(parent, "$baseName ($suffix)")
            suffix++
        }
        return candidate
    }

    private fun normalizedName(requestedName: String, stripMarkdownExtension: Boolean): String {
        var name = requestedName.trim()
        if (stripMarkdownExtension && name.endsWith(".md", ignoreCase = true)) {
            name = name.dropLast(3).trimEnd()
        }
        require(name.isNotBlank()) { "名称不能为空" }
        require(name != "." && name != "..") { "名称无效" }
        require(name.none { it == '/' || it == '\u0000' }) { "名称不能包含 /" }
        return name.take(MAX_NAME_LENGTH)
    }

    private fun relativePath(file: File): String =
        file.relativeTo(root).invariantSeparatorsPath

    companion object {
        const val NOTE_FILE = "note.md"
        const val ASSETS_DIRECTORY = "assets"
        private const val MAX_NAME_LENGTH = 80
    }
}

internal object NotePathPolicy {
    fun resolve(root: File, relativePath: String): File? {
        if (File(relativePath).isAbsolute) return null
        val canonicalRoot = runCatching { root.canonicalFile }.getOrNull() ?: return null
        val candidate = runCatching { File(canonicalRoot, relativePath).canonicalFile }.getOrNull()
            ?: return null
        return candidate.takeIf {
            it == canonicalRoot || it.toPath().startsWith(canonicalRoot.toPath())
        }
    }
}
