package com.mlevngr.inknote.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WebDavSyncEngineTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun uploadsLocalOnlyFilesAndPersistsTheirVersion() {
        val fixture = fixture()
        fixture.local("Ideas.note/note.md", "local")

        val report = fixture.sync()

        assertEquals("local", fixture.remote.text("Ideas.note/note.md"))
        assertEquals(1, report.uploaded)
        assertTrue(fixture.records().containsKey("Ideas.note/note.md"))
    }

    @Test fun preservesEmptyFoldersWithAnInternalMarkerFile() {
        val fixture = fixture()
        File(fixture.root, "Empty.folder").mkdirs()

        fixture.sync()

        assertEquals("", fixture.remote.text("Empty.folder/.mote-folder"))
    }

    @Test fun downloadsRemoteOnlyFilesWithoutTrustingRemotePaths() {
        val fixture = fixture()
        fixture.remote.put("Inbox.note/note.md", "remote")

        val report = fixture.sync()

        assertEquals("remote", fixture.file("Inbox.note/note.md").readText())
        assertEquals(1, report.downloaded)
    }

    @Test fun downloadsRemoteChangesAndUploadsLocalChanges() {
        val fixture = fixture()
        fixture.local("A.note/note.md", "one")
        fixture.local("B.note/note.md", "one")
        fixture.sync()
        fixture.remote.put("A.note/note.md", "remote two")
        fixture.local("B.note/note.md", "local two")

        val report = fixture.sync()

        assertEquals("remote two", fixture.file("A.note/note.md").readText())
        assertEquals("local two", fixture.remote.text("B.note/note.md"))
        assertEquals(1, report.downloaded)
        assertEquals(1, report.uploaded)
    }

    @Test fun concurrentChangesCreateConflictCopyAndKeepLocalAtOriginalPath() {
        val fixture = fixture(now = 1_700_000_000_000L)
        fixture.local("Ideas.note/note.md", "base")
        fixture.sync()
        fixture.local("Ideas.note/note.md", "local edit")
        fixture.remote.put("Ideas.note/note.md", "remote edit")

        val report = fixture.sync()

        assertEquals("local edit", fixture.remote.text("Ideas.note/note.md"))
        val conflict = fixture.root.listFiles().orEmpty().single { "同步冲突" in it.name }
        assertEquals("remote edit", File(conflict, "note.md").readText())
        assertEquals("remote edit", fixture.remote.text("${conflict.name}/note.md"))
        assertEquals(1, report.conflicts)
    }

    @Test fun localDeletionRemovesPreviouslySynchronizedRemoteFile() {
        val fixture = fixture()
        fixture.local("Old.note/note.md", "old")
        fixture.sync()
        fixture.file("Old.note/note.md").delete()

        val report = fixture.sync()

        assertFalse(fixture.remote.contains("Old.note/note.md"))
        assertEquals(1, report.deletedRemote)
    }

    @Test fun remoteDeletionRemovesAnUnchangedLocalFileInsteadOfResurrectingIt() {
        val fixture = fixture()
        fixture.local("Old.note/note.md", "old")
        fixture.sync()
        fixture.remote.delete("Old.note/note.md")

        val report = fixture.sync()

        assertFalse(fixture.file("Old.note/note.md").exists())
        assertFalse(fixture.file("Old.note").exists())
        assertEquals(1, report.deletedLocal)
    }

    @Test fun remoteDeletionAndLocalEditKeepsTheEditAndReportsAConflict() {
        val fixture = fixture()
        fixture.local("Draft.note/note.md", "base")
        fixture.sync()
        fixture.remote.delete("Draft.note/note.md")
        fixture.local("Draft.note/note.md", "keep me")

        val report = fixture.sync()

        assertEquals("keep me", fixture.remote.text("Draft.note/note.md"))
        assertEquals(1, report.conflicts)
    }

    @Test fun localDeletionAndRemoteEditPreservesTheRemoteEditAsAConflictCopy() {
        val fixture = fixture(now = 1_700_000_000_000L)
        fixture.local("Draft.note/note.md", "base")
        fixture.sync()
        fixture.file("Draft.note/note.md").delete()
        fixture.remote.put("Draft.note/note.md", "remote edit")

        val report = fixture.sync()

        val conflict = fixture.root.listFiles().orEmpty().single { "同步冲突" in it.name }
        assertEquals("remote edit", File(conflict, "note.md").readText())
        assertFalse(fixture.remote.contains("Draft.note/note.md"))
        assertEquals(1, report.conflicts)
    }

    @Test fun multipleConflictingFilesInOneNoteShareOneConflictCopy() {
        val fixture = fixture(now = 1_700_000_000_000L)
        fixture.local("Draft.note/note.md", "base")
        fixture.local("Draft.note/assets/photo.txt", "base asset")
        fixture.sync()
        fixture.local("Draft.note/note.md", "local text")
        fixture.local("Draft.note/assets/photo.txt", "local asset")
        fixture.remote.put("Draft.note/note.md", "remote text")
        fixture.remote.put("Draft.note/assets/photo.txt", "remote asset")

        fixture.sync()

        val conflicts = fixture.root.listFiles().orEmpty().filter { "同步冲突" in it.name }
        assertEquals(1, conflicts.size)
        assertEquals("remote text", File(conflicts.single(), "note.md").readText())
        assertEquals("remote asset", File(conflicts.single(), "assets/photo.txt").readText())
    }

    private fun fixture(now: Long = 1L): Fixture {
        val root = temporaryFolder.newFolder("notes-${System.nanoTime()}")
        val state = WebDavSyncState(File(root.parentFile, "state-${System.nanoTime()}.properties"))
        return Fixture(root, state, FakeRemote(), now)
    }

    private class Fixture(
        val root: File,
        private val state: WebDavSyncState,
        val remote: FakeRemote,
        private val now: Long
    ) {
        private val identity = "http://nas/\n\nuser\nMote"
        private val engine = WebDavSyncEngine(root, state) { now }
        fun file(path: String) = SyncPathPolicy.resolve(root, path)
        fun local(path: String, text: String) = file(path).apply { parentFile?.mkdirs(); writeText(text) }
        fun sync() = engine.sync(remote, identity)
        fun records() = state.load(identity)
    }

    private class FakeRemote : WebDavRemote {
        override val endpoint = WebDavEndpoint(WebDavEndpoint.Kind.Internal, "http://nas/")
        private val data = mutableMapOf<String, Pair<String, Int>>()
        private var version = 0
        override fun ensureRoot() = Unit
        override fun listFiles() = data.map { (path, value) ->
            RemoteFile(path, "v${value.second}", 0, value.first.toByteArray().size.toLong())
        }
        override fun download(path: String, destination: File) {
            destination.parentFile?.mkdirs()
            destination.writeText(requireNotNull(data[path]).first)
        }
        override fun upload(path: String, source: File): RemoteFile {
            put(path, source.readText())
            val value = requireNotNull(data[path])
            return RemoteFile(path, "v${value.second}", 0, source.length())
        }
        override fun delete(path: String) { data.remove(path) }
        fun put(path: String, text: String) { data[path] = text to ++version }
        fun text(path: String) = requireNotNull(data[path]).first
        fun contains(path: String) = path in data
    }
}
