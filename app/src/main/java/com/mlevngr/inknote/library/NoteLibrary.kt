package com.mlevngr.inknote.library

import android.content.Context
import com.mlevngr.inknote.assets.AssetPathPolicy
import java.io.File

class NoteLibrary internal constructor(private val root: File) {
    constructor(context: Context) : this(File(context.filesDir, "notes"))

    init {
        root.mkdirs()
    }

    enum class EntryType { Folder, Note }

    data class FolderLocation(val names: List<String>) {
        val displayPath: String get() = names.joinToString(" / ")
        val title: String? get() = names.lastOrNull()
        fun child(name: String): FolderLocation = FolderLocation(names + name)
        fun parent(): FolderLocation? = names.dropLast(1)
            .takeIf { names.isNotEmpty() }
            ?.let(::FolderLocation)

        companion object {
            val Root = FolderLocation(emptyList())
        }
    }

    data class Entry(
        val name: String,
        val relativePath: String,
        val type: EntryType,
        val modifiedAt: Long,
        val childCount: Int = 0,
        val folderColor: FolderColor = FolderColor.Blue,
        val preview: NotePreview = NotePreview("", null),
        val previewImage: File? = null
    )

    fun list(location: FolderLocation): List<Entry> = requireFolder(location)
        .listFiles().orEmpty()
        .filter { it.isDirectory && !it.name.startsWith('.') }
        .map { directory ->
            val type = typeOf(directory)
            entry(directory, type)
        }
        .sortedWith(compareBy<Entry> { it.type != EntryType.Folder }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    fun createFolder(location: FolderLocation, requestedName: String): Entry {
        val parent = requireFolder(location)
        val name = normalizedName(requestedName, stripMarkdownExtension = false)
        val folder = uniqueTypedChild(parent, name, EntryType.Folder)
        check(folder.mkdir()) { "无法创建文件夹" }
        return folderEntry(folder)
    }

    fun createNote(location: FolderLocation, requestedName: String): Entry {
        val parent = requireFolder(location)
        val name = normalizedName(requestedName, stripMarkdownExtension = true)
        val note = uniqueTypedChild(parent, name, EntryType.Note)
        check(note.mkdir()) { "无法创建笔记" }
        File(note, ASSETS_DIRECTORY).mkdirs()
        File(note, NOTE_FILE).writeText("")
        File(note, BODY_SEPARATION_MARKER).writeText("")
        return noteEntry(note)
    }

    fun listFolderLocations(): List<FolderLocation> = buildList {
        fun collect(parent: File, location: FolderLocation) {
            parent.listFiles().orEmpty()
                .filter { it.isDirectory && !it.name.startsWith('.') }
                .filter { typeOf(it) == EntryType.Folder }
                .forEach { folder ->
                    val child = location.child(displayName(folder, EntryType.Folder))
                    add(child)
                    collect(folder, child)
                }
        }
        collect(root, FolderLocation.Root)
    }.sortedBy { it.displayPath.lowercase() }

    fun moveNote(
        sourceLocation: FolderLocation,
        noteName: String,
        targetLocation: FolderLocation
    ): Entry {
        val source = findChild(sourceLocation, noteName, EntryType.Note)
        val targetFolder = requireFolder(targetLocation)
        if (sameFile(source.parentFile, targetFolder)) return noteEntry(source)
        val destination = uniqueTypedChild(
            targetFolder,
            displayName(source, EntryType.Note),
            EntryType.Note
        )
        check(source.renameTo(destination)) { "无法移动笔记" }
        return noteEntry(destination)
    }

    fun moveFolder(
        sourceLocation: FolderLocation,
        folderName: String,
        targetLocation: FolderLocation
    ): Entry {
        val source = findChild(sourceLocation, folderName, EntryType.Folder)
        val targetFolder = requireFolder(targetLocation)
        if (sameFile(source.parentFile, targetFolder)) return folderEntry(source)
        require(!isSameOrDescendant(targetFolder, source)) { "不能把文件夹移动到自身或其子文件夹中" }
        val destination = uniqueTypedChild(
            targetFolder,
            displayName(source, EntryType.Folder),
            EntryType.Folder
        )
        check(source.renameTo(destination)) { "无法移动文件夹" }
        return folderEntry(destination)
    }

    fun deleteNote(location: FolderLocation, noteName: String) {
        val note = findChild(location, noteName, EntryType.Note)
        check(note.deleteRecursively() && !note.exists()) { "无法删除笔记" }
    }

    fun renameNote(location: FolderLocation, noteName: String, requestedName: String): Entry =
        noteEntry(renameChild(location, noteName, requestedName, EntryType.Note))

    fun findFolder(location: FolderLocation, folderName: String): Entry =
        folderEntry(findChild(location, folderName, EntryType.Folder))

    fun findNote(location: FolderLocation, noteName: String): Entry =
        noteEntry(findChild(location, noteName, EntryType.Note))

    internal fun findNoteDirectory(location: FolderLocation, noteName: String): File =
        findChild(location, noteName, EntryType.Note)

    fun deleteFolder(location: FolderLocation, folderName: String) {
        val folder = findChild(location, folderName, EntryType.Folder)
        check(folder.deleteRecursively() && !folder.exists()) { "无法删除文件夹" }
    }

    fun renameFolder(location: FolderLocation, folderName: String, requestedName: String): Entry =
        folderEntry(renameChild(location, folderName, requestedName, EntryType.Folder))

    fun setFolderColor(location: FolderLocation, folderName: String, color: FolderColor): Entry {
        val folder = findChild(location, folderName, EntryType.Folder)
        File(folder, FOLDER_COLOR_FILE).writeText(color.id)
        return folderEntry(folder)
    }

    private fun requireFolder(location: FolderLocation): File {
        var current = root
        location.names.forEach { name ->
            current = findChild(current, name, EntryType.Folder)
        }
        require(current.isDirectory && typeOf(current) == EntryType.Folder) {
            "文件夹不存在或已经移动"
        }
        return current
    }

    private fun uniqueTypedChild(parent: File, baseName: String, type: EntryType): File {
        val usedNames = parent.listFiles().orEmpty()
            .filter(File::isDirectory)
            .mapNotNull { child ->
                val childType = typeOf(child)
                if (childType == type) displayName(child, childType) else null
            }
            .toSet()
        var displayName = baseName
        var suffix = 1
        while (displayName in usedNames) {
            displayName = "$baseName ($suffix)"
            suffix++
        }
        return File(parent, "$displayName${directorySuffix(type)}")
    }

    private fun findChild(location: FolderLocation, childName: String, type: EntryType): File =
        findChild(requireFolder(location), childName, type)

    private fun findChild(parent: File, childName: String, type: EntryType): File {
        val matches = parent.listFiles().orEmpty().filter { child ->
            child.isDirectory && typeOf(child) == type && displayName(child, type) == childName
        }
        require(matches.size == 1) {
            if (type == EntryType.Note) "笔记不存在或已经移动" else "文件夹不存在或已经移动"
        }
        return matches.single()
    }

    private fun renameChild(
        location: FolderLocation,
        currentName: String,
        requestedName: String,
        type: EntryType
    ): File {
        val source = findChild(location, currentName, type)
        val newName = normalizedName(requestedName, stripMarkdownExtension = type == EntryType.Note)
        if (newName == displayName(source, type)) return source

        val parent = requireNotNull(source.parentFile)
        val duplicate = parent.listFiles().orEmpty().any { child ->
            child.isDirectory && child != source && typeOf(child) == type &&
                displayName(child, type) == newName
        }
        require(!duplicate) {
            if (type == EntryType.Note) "同名笔记已存在" else "同名文件夹已存在"
        }

        val destination = File(parent, "$newName${directorySuffix(type)}")
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

    private fun entry(directory: File, type: EntryType): Entry {
        val preview = if (type == EntryType.Note) {
            runCatching {
                File(directory, NOTE_FILE).bufferedReader().use { reader ->
                    val buffer = CharArray(MAX_PREVIEW_SOURCE_LENGTH)
                    val length = reader.read(buffer).coerceAtLeast(0)
                    NotePreviewExtractor.extract(buffer.concatToString(0, length))
                }
            }.getOrDefault(NotePreview("", null))
        } else NotePreview("", null)
        return Entry(
            name = displayName(directory, type),
            relativePath = directory.relativeTo(root).invariantSeparatorsPath,
            type = type,
            modifiedAt = if (type == EntryType.Note) {
                File(directory, NOTE_FILE).lastModified()
            } else directory.lastModified(),
            childCount = if (type == EntryType.Folder) {
                directory.listFiles().orEmpty().count { it.isDirectory && !it.name.startsWith('.') }
            } else 0,
            folderColor = if (type == EntryType.Folder) {
                FolderColor.fromId(runCatching { File(directory, FOLDER_COLOR_FILE).readText().trim() }.getOrNull())
            } else FolderColor.Blue,
            preview = preview,
            previewImage = preview.imageRelativePath?.let { relative ->
                AssetPathPolicy.resolve(directory, relative)?.takeIf(File::isFile)
            }
        )
    }

    private fun noteEntry(directory: File): Entry = entry(directory, EntryType.Note)
    private fun folderEntry(directory: File): Entry = entry(directory, EntryType.Folder)

    private fun typeOf(directory: File): EntryType =
        if (File(directory, NOTE_FILE).isFile) EntryType.Note else EntryType.Folder

    private fun displayName(directory: File, type: EntryType): String =
        directory.name.removeSuffix(directorySuffix(type))

    private fun directorySuffix(type: EntryType): String =
        if (type == EntryType.Note) NOTE_DIRECTORY_SUFFIX else FOLDER_DIRECTORY_SUFFIX

    private fun sameFile(first: File?, second: File): Boolean =
        first?.canonicalFile == second.canonicalFile

    private fun isSameOrDescendant(candidate: File, ancestor: File): Boolean {
        val candidatePath = candidate.canonicalFile.path
        val ancestorPath = ancestor.canonicalFile.path
        val prefix = if (ancestorPath.endsWith(File.separator)) ancestorPath
        else "$ancestorPath${File.separator}"
        return candidatePath == ancestorPath || candidatePath.startsWith(prefix)
    }

    companion object {
        const val NOTE_FILE = "note.md"
        const val ASSETS_DIRECTORY = "assets"
        const val BODY_SEPARATION_MARKER = ".body-title-separated-v1"
        private const val FOLDER_COLOR_FILE = ".inknote-folder-color"
        private const val NOTE_DIRECTORY_SUFFIX = ".note"
        private const val FOLDER_DIRECTORY_SUFFIX = ".folder"
        private const val MAX_NAME_LENGTH = 80
        private const val MAX_PREVIEW_SOURCE_LENGTH = 32_768
    }
}
