package com.mlevngr.inknote.backup

import com.mlevngr.inknote.library.NoteLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class VaultArchiveTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun exportDeleteAndRestorePreservesMarkdownAttachmentsFoldersAndUnicodeNames() {
        val vault = temporaryFolder.newFolder("vault")
        val library = NoteLibrary(vault)
        val folder = library.createFolder(NoteLibrary.FolderLocation.Root, "资料 📚")
        val note = library.createNote(
            NoteLibrary.FolderLocation.Root.child(folder.name),
            "计划 [v1]"
        )
        File(vault, "${note.relativePath}/note.md")
            .writeText("# 计划\n\n![图](assets/图片 #1.png)")
        File(vault, "${note.relativePath}/assets/图片 #1.png")
            .writeBytes(byteArrayOf(0, 1, 2, 127, -1))
        File(vault, "${note.relativePath}/assets/讲义 (最终版).pdf")
            .writeBytes("%PDF-1.7\n测试".toByteArray())
        library.createFolder(NoteLibrary.FolderLocation.Root, "空文件夹")
        val before = hashes(vault)
        val archive = ByteArrayOutputStream().also { VaultArchive.create(vault, it, 1234L) }

        vault.deleteRecursively()
        VaultArchive.restore(ByteArrayInputStream(archive.toByteArray()), vault)

        assertEquals(before, hashes(vault))
        assertTrue(File(vault, "空文件夹.folder").isDirectory)
    }

    @Test fun invalidArchiveNeverChangesExistingVault() {
        val vault = temporaryFolder.newFolder("existing")
        val note = File(vault, "Safe.note/note.md")
        requireNotNull(note.parentFile).mkdirs()
        note.writeText("keep me")

        val failure = runCatching {
            VaultArchive.restore(ByteArrayInputStream("not a zip".toByteArray()), vault)
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals("keep me", note.readText())
    }

    @Test fun excludesInterruptedTemporaryWritesFromExport() {
        val vault = temporaryFolder.newFolder("temporary-files")
        File(vault, "Safe.note").mkdirs()
        File(vault, "Safe.note/note.md").writeText("stable")
        File(vault, "Safe.note/note.md.tmp-deadbeef.part").writeText("partial")
        val archive = ByteArrayOutputStream().also { VaultArchive.create(vault, it) }
        val paths = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(archive.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                paths += entry.name
            }
        }

        assertTrue("Safe.note/note.md" in paths)
        assertFalse(paths.any { ".tmp-" in it })
    }

    @Test fun rejectsArchivePathTraversalWithoutWritingOutsideVault() {
        val vault = File(temporaryFolder.root, "vault")
        val archive = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry(VaultArchive.MANIFEST_PATH))
                zip.write("formatVersion=1\n".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("../escaped.txt"))
                zip.write("bad".toByteArray())
                zip.closeEntry()
            }
        }

        val failure = runCatching {
            VaultArchive.restore(ByteArrayInputStream(archive.toByteArray()), vault)
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertFalse(File(temporaryFolder.root, "escaped.txt").exists())
        assertFalse(vault.exists())
    }

    private fun hashes(root: File): Map<String, String> = root.walkTopDown()
        .filter(File::isFile)
        .associate { file ->
            file.relativeTo(root).invariantSeparatorsPath to
                MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                    .joinToString("") { "%02x".format(it) }
        }
}
