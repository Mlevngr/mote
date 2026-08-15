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
                    name = displayName(directory, type),
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
        val folder = uniqueTypedChild(parent, name, EntryType.Folder)
        check(folder.mkdir()) { "无法创建文件夹" }
        return Entry(
            name = displayName(folder, EntryType.Folder),
            relativePath = relativePath(folder),
            type = EntryType.Folder,
            modifiedAt = folder.lastModified()
        )
    }

    fun createNote(parentPath: String, requestedName: String): Entry {
        val parent = requireFolder(parentPath)
        val name = normalizedName(requestedName, stripMarkdownExtension = true)
        val note = uniqueTypedChild(parent, name, EntryType.Note)
        check(note.mkdir()) { "无法创建笔记" }
        File(note, ASSETS_DIRECTORY).mkdirs()
        File(note, NOTE_FILE).writeText("# $name\n")
        return Entry(
            name = displayName(note, EntryType.Note),
            relativePath = relativePath(note),
            type = EntryType.Note,
            modifiedAt = System.currentTimeMillis()
        )
    }

    fun listFolders(): List<Entry> = buildList {
        fun collect(parent: File) {
            parent.listFiles().orEmpty()
                .filter { it.isDirectory && !it.name.startsWith('.') }
                .filterNot { File(it, NOTE_FILE).isFile }
                .forEach { folder ->
                    add(
                        Entry(
                            name = displayName(folder, EntryType.Folder),
                            relativePath = relativePath(folder),
                            type = EntryType.Folder,
                            modifiedAt = folder.lastModified()
                        )
                    )
                    collect(folder)
                }
        }
        collect(root)
    }.sortedBy { it.relativePath.lowercase() }

    fun moveNote(sourceFolderPath: String, noteName: String, targetFolderPath: String): Entry {
        val source = findChild(sourceFolderPath, noteName, EntryType.Note)
        val targetFolder = requireFolder(targetFolderPath)
        if (source.parentFile?.canonicalFile == targetFolder.canonicalFile) {
            return noteEntry(source)
        }
        val destination = uniqueTypedChild(
            targetFolder,
            displayName(source, EntryType.Note),
            EntryType.Note
        )
        check(source.renameTo(destination)) { "无法移动笔记" }
        return noteEntry(destination)
    }

    fun deleteNote(folderPath: String, noteName: String) {
        val note = findChild(folderPath, noteName, EntryType.Note)
        check(note.deleteRecursively() && !note.exists()) { "无法删除笔记" }
    }

    fun renameNote(folderPath: String, noteName: String, requestedName: String): Entry =
        noteEntry(renameChild(folderPath, noteName, requestedName, EntryType.Note))

    fun findFolder(parentPath: String, folderName: String): Entry =
        folderEntry(findChild(parentPath, folderName, EntryType.Folder))

    fun findNote(parentPath: String, noteName: String): Entry =
        noteEntry(findChild(parentPath, noteName, EntryType.Note))

    internal fun findNoteDirectory(parentPath: String, noteName: String): File =
        findChild(parentPath, noteName, EntryType.Note)

    fun deleteFolder(parentPath: String, folderName: String) {
        val folder = findChild(parentPath, folderName, EntryType.Folder)
        check(folder.deleteRecursively() && !folder.exists()) { "无法删除文件夹" }
    }

    fun renameFolder(parentPath: String, folderName: String, requestedName: String): Entry =
        folderEntry(renameChild(parentPath, folderName, requestedName, EntryType.Folder))

    fun parentOf(folderPath: String): String? {
        if (folderPath.isBlank()) return null
        return folderPath.substringBeforeLast('/', "")
    }

    fun displayPath(folderPath: String): String {
        if (folderPath.isBlank()) return ""
        val folder = NotePathPolicy.resolve(root, folderPath) ?: return folderPath
        return generateSequence(folder) { current ->
            current.parentFile?.takeIf { it != root }
        }.toList().asReversed().joinToString(" / ") {
            displayName(it, EntryType.Folder)
        }
    }

    private fun requireFolder(folderPath: String): File {
        val folder = NotePathPolicy.resolve(root, folderPath)
            ?: error("无效的文件夹路径")
        require(folder.isDirectory) { "文件夹不存在" }
        require(!File(folder, NOTE_FILE).exists()) { "笔记不能包含子项目" }
        return folder
    }

    private fun uniqueTypedChild(parent: File, baseName: String, type: EntryType): File {
        val usedNames = parent.listFiles().orEmpty()
            .filter(File::isDirectory)
            .mapNotNull { child ->
                val childType = if (File(child, NOTE_FILE).isFile) EntryType.Note else EntryType.Folder
                if (childType == type) displayName(child, childType) else null
            }
            .toSet()
        var displayName = baseName
        var suffix = 1
        while (displayName in usedNames) {
            displayName = "$baseName ($suffix)"
            suffix++
        }
        val marker = if (type == EntryType.Note) NOTE_DIRECTORY_SUFFIX else FOLDER_DIRECTORY_SUFFIX
        return File(parent, "$displayName$marker")
    }

    private fun findChild(parentPath: String, childName: String, type: EntryType): File {
        val parent = requireFolder(parentPath)
        val matches = parent.listFiles().orEmpty().filter { child ->
            if (!child.isDirectory) return@filter false
            val childType = if (File(child, NOTE_FILE).isFile) EntryType.Note else EntryType.Folder
            childType == type && displayName(child, childType) == childName
        }
        require(matches.size == 1) {
            if (type == EntryType.Note) "笔记不存在或已经移动" else "文件夹不存在或已经移动"
        }
        return matches.single()
    }

    private fun renameChild(
        parentPath: String,
        currentName: String,
        requestedName: String,
        type: EntryType
    ): File {
        val source = findChild(parentPath, currentName, type)
        val newName = normalizedName(requestedName, stripMarkdownExtension = type == EntryType.Note)
        if (newName == displayName(source, type)) return source

        val parent = requireNotNull(source.parentFile)
        val duplicate = parent.listFiles().orEmpty().any { child ->
            if (!child.isDirectory || child == source) return@any false
            val childType = if (File(child, NOTE_FILE).isFile) EntryType.Note else EntryType.Folder
            childType == type && displayName(child, childType) == newName
        }
        require(!duplicate) {
            if (type == EntryType.Note) "同名笔记已存在" else "同名文件夹已存在"
        }

        val marker = if (type == EntryType.Note) NOTE_DIRECTORY_SUFFIX else FOLDER_DIRECTORY_SUFFIX
        val destination = File(parent, "$newName$marker")
        require(!destination.exists()) { "目标名称已被占用" }
        check(source.renameTo(destination)) {
            if (type == EntryType.Note) "无法重命名笔记" else "无法重命名文件夹"
        }
        return destination
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

    private fun noteEntry(directory: File): Entry = Entry(
        name = displayName(directory, EntryType.Note),
        relativePath = relativePath(directory),
        type = EntryType.Note,
        modifiedAt = File(directory, NOTE_FILE).lastModified()
    )

    private fun folderEntry(directory: File): Entry = Entry(
        name = displayName(directory, EntryType.Folder),
        relativePath = relativePath(directory),
        type = EntryType.Folder,
        modifiedAt = directory.lastModified()
    )

    private fun displayName(directory: File, type: EntryType): String {
        val marker = if (type == EntryType.Note) NOTE_DIRECTORY_SUFFIX else FOLDER_DIRECTORY_SUFFIX
        return directory.name.removeSuffix(marker)
    }

    companion object {
        const val NOTE_FILE = "note.md"
        const val ASSETS_DIRECTORY = "assets"
        private const val NOTE_DIRECTORY_SUFFIX = ".note"
        private const val FOLDER_DIRECTORY_SUFFIX = ".folder"
        private const val MAX_NAME_LENGTH = 80
    }
}

internal object NotePathPolicy {
    fun resolve(root: File, relativePath: String): File? {
        if (File(relativePath).isAbsolute) return null
        val canonicalRoot = runCatching { root.canonicalFile }.getOrNull() ?: return null
        val candidate = runCatching { File(canonicalRoot, relativePath).canonicalFile }.getOrNull()
            ?: return null
        val rootPath = canonicalRoot.path
        val childPrefix = if (rootPath.endsWith(File.separator)) rootPath else "$rootPath${File.separator}"
        return candidate.takeIf { it.path == rootPath || it.path.startsWith(childPrefix) }
    }
}
