package com.mlevngr.inknote

import android.os.Bundle
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.mlevngr.inknote.library.NoteLibrary
import com.mlevngr.inknote.library.NoteLibrary.FolderLocation
import com.mlevngr.inknote.ui.NoteLibraryAdapter
import com.mlevngr.inknote.ui.SystemBarInsets

class MainActivity : AppCompatActivity() {
    private lateinit var library: NoteLibrary
    private lateinit var adapter: NoteLibraryAdapter
    private lateinit var toolbar: MaterialToolbar
    private lateinit var emptyView: TextView
    private var currentFolder = FolderLocation.Root

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library)
        SystemBarInsets.install(findViewById(R.id.library_root))

        library = NoteLibrary(this)
        currentFolder = runCatching {
            savedInstanceState
                ?.getStringArrayList(STATE_FOLDER)
                ?.let { FolderLocation(it.toList()) }
        }.getOrNull() ?: FolderLocation.Root
        adapter = NoteLibraryAdapter(this, ::openEntry, ::showEntryActions)
        toolbar = findViewById(R.id.library_toolbar)
        emptyView = findViewById(R.id.empty_library)
        findViewById<RecyclerView>(R.id.library_list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
            itemAnimator = null
        }
        findViewById<AppCompatImageButton>(R.id.create_folder).setOnClickListener {
            showCreateDialog(CreateType.Folder)
        }
        findViewById<AppCompatImageButton>(R.id.create_note).setOnClickListener {
            showCreateDialog(CreateType.Note)
        }
        toolbar.setNavigationOnClickListener { navigateUp() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentFolder.names.isNotEmpty()) navigateUp()
                else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList(STATE_FOLDER, ArrayList(currentFolder.names))
        super.onSaveInstanceState(outState)
    }

    private fun refresh() {
        val entries = runCatching { library.list(currentFolder) }.getOrElse { error ->
            currentFolder = FolderLocation.Root
            Toast.makeText(
                this,
                getString(R.string.open_failed, error.message ?: getString(R.string.unknown_error)),
                Toast.LENGTH_LONG
            ).show()
            runCatching { library.list(FolderLocation.Root) }.getOrDefault(emptyList())
        }
        adapter.submit(entries)
        emptyView.visibility = if (entries.isEmpty()) android.view.View.VISIBLE
        else android.view.View.GONE
        toolbar.title = currentFolder.title ?: getString(R.string.app_name)
        if (currentFolder.names.isEmpty()) toolbar.navigationIcon = null
        else toolbar.setNavigationIcon(R.drawable.ic_arrow_back_24)
        toolbar.navigationContentDescription = getString(R.string.go_up)
    }

    private fun openEntry(entry: NoteLibrary.Entry) {
        runCatching {
            when (entry.type) {
                NoteLibrary.EntryType.Folder -> {
                    library.findFolder(currentFolder, entry.name)
                    currentFolder = currentFolder.child(entry.name)
                    refresh()
                }
                NoteLibrary.EntryType.Note -> {
                    val note = library.findNote(currentFolder, entry.name)
                    startActivity(EditorActivity.intent(this, currentFolder, note.name))
                }
            }
        }.onFailure {
            Toast.makeText(
                this,
                getString(R.string.open_failed, it.message ?: getString(R.string.unknown_error)),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun navigateUp() {
        currentFolder = currentFolder.parent() ?: FolderLocation.Root
        refresh()
    }

    private fun showCreateDialog(type: CreateType) {
        val input = TextInputEditText(this).apply {
            setSingleLine()
        }
        val inputLayout = TextInputLayout(this).apply {
            hint = getString(if (type == CreateType.Folder) R.string.folder_name else R.string.note_name)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            addView(input, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        val margin = (24 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(this).apply {
            addView(inputLayout, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = margin
                marginEnd = margin
                topMargin = margin / 2
            })
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (type == CreateType.Folder) R.string.create_folder else R.string.create_note)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.create, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                runCatching {
                    when (type) {
                        CreateType.Folder -> library.createFolder(currentFolder, input.text.toString())
                        CreateType.Note -> library.createNote(currentFolder, input.text.toString())
                    }
                }.onSuccess { entry ->
                    dialog.dismiss()
                    refresh()
                    if (entry.type == NoteLibrary.EntryType.Note) openEntry(entry)
                }.onFailure {
                    inputLayout.error = it.message ?: getString(R.string.create_failed)
                    Toast.makeText(this, it.message ?: getString(R.string.create_failed), Toast.LENGTH_LONG).show()
                }
            }
        }
        dialog.show()
        input.requestFocus()
    }

    private fun showEntryActions(entry: NoteLibrary.Entry) {
        val actions = if (entry.type == NoteLibrary.EntryType.Note) {
            arrayOf(
                getString(R.string.rename_note),
                getString(R.string.move_note),
                getString(R.string.delete_note)
            )
        } else {
            arrayOf(
                getString(R.string.rename_folder),
                getString(R.string.move_folder),
                getString(R.string.delete_folder)
            )
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(entry.name)
            .setItems(actions) { _, which ->
                when (entry.type) {
                    NoteLibrary.EntryType.Note -> when (which) {
                        0 -> showRenameDialog(entry)
                        1 -> showMoveNoteDialog(entry)
                        2 -> showDeleteNoteConfirmation(entry)
                    }
                    NoteLibrary.EntryType.Folder -> when (which) {
                        0 -> showRenameDialog(entry)
                        1 -> showMoveFolderDialog(entry)
                        2 -> showDeleteFolderConfirmation(entry)
                    }
                }
            }
            .show()
    }

    private fun showRenameDialog(entry: NoteLibrary.Entry) {
        val input = TextInputEditText(this).apply {
            setSingleLine()
            setText(entry.name)
            setSelection(text?.length ?: 0)
        }
        val inputLayout = TextInputLayout(this).apply {
            hint = getString(
                if (entry.type == NoteLibrary.EntryType.Note) R.string.note_name
                else R.string.folder_name
            )
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            addView(input, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        val margin = (24 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(this).apply {
            addView(inputLayout, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = margin
                marginEnd = margin
                topMargin = margin / 2
            })
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(
                if (entry.type == NoteLibrary.EntryType.Note) R.string.rename_note
                else R.string.rename_folder
            )
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.rename, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                runCatching {
                    when (entry.type) {
                        NoteLibrary.EntryType.Note ->
                            library.renameNote(currentFolder, entry.name, input.text.toString())
                        NoteLibrary.EntryType.Folder ->
                            library.renameFolder(currentFolder, entry.name, input.text.toString())
                    }
                }.onSuccess {
                    dialog.dismiss()
                    refresh()
                    Toast.makeText(this, R.string.renamed, Toast.LENGTH_SHORT).show()
                }.onFailure {
                    inputLayout.error = it.message ?: getString(R.string.rename_failed)
                }
            }
        }
        dialog.show()
        input.requestFocus()
    }

    private fun showDeleteNoteConfirmation(note: NoteLibrary.Entry) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_note)
            .setMessage(getString(R.string.delete_note_confirmation, note.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                runCatching { library.deleteNote(currentFolder, note.name) }
                    .onSuccess {
                        refresh()
                        Toast.makeText(this, R.string.note_deleted, Toast.LENGTH_SHORT).show()
                    }
                    .onFailure {
                        Toast.makeText(
                            this,
                            it.message ?: getString(R.string.delete_failed),
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .show()
    }

    private fun showDeleteFolderConfirmation(folder: NoteLibrary.Entry) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_folder)
            .setMessage(getString(R.string.delete_folder_confirmation, folder.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                runCatching { library.deleteFolder(currentFolder, folder.name) }
                    .onSuccess {
                        refresh()
                        Toast.makeText(this, R.string.folder_deleted, Toast.LENGTH_SHORT).show()
                    }
                    .onFailure {
                        Toast.makeText(
                            this,
                            it.message ?: getString(R.string.delete_failed),
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .show()
    }

    private fun showMoveNoteDialog(note: NoteLibrary.Entry) {
        val destinations = (listOf(FolderLocation.Root) + library.listFolderLocations())
            .filter { it != currentFolder }
        if (destinations.isEmpty()) {
            Toast.makeText(this, R.string.no_move_destination, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = destinations.map(::folderLabel).toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.move_to)
            .setItems(labels) { _, which ->
                val target = destinations[which]
                runCatching { library.moveNote(currentFolder, note.name, target) }
                    .onSuccess {
                        refresh()
                        Toast.makeText(this, getString(R.string.note_moved), Toast.LENGTH_SHORT).show()
                    }
                    .onFailure {
                        Toast.makeText(this, it.message ?: getString(R.string.move_failed), Toast.LENGTH_LONG).show()
                    }
            }
            .show()
    }

    private fun showMoveFolderDialog(folder: NoteLibrary.Entry) {
        val source = currentFolder.child(folder.name)
        val destinations = (listOf(FolderLocation.Root) + library.listFolderLocations())
            .filter { target ->
                target != currentFolder && !target.isWithin(source)
            }
        if (destinations.isEmpty()) {
            Toast.makeText(this, R.string.no_move_destination, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = destinations.map(::folderLabel).toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.move_to)
            .setItems(labels) { _, which ->
                runCatching { library.moveFolder(currentFolder, folder.name, destinations[which]) }
                    .onSuccess {
                        refresh()
                        Toast.makeText(this, R.string.folder_moved, Toast.LENGTH_SHORT).show()
                    }
                    .onFailure {
                        Toast.makeText(
                            this,
                            it.message ?: getString(R.string.move_failed),
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .show()
    }

    private fun folderLabel(location: FolderLocation): String =
        location.displayPath.ifBlank { getString(R.string.library_root) }

    private fun FolderLocation.isWithin(ancestor: FolderLocation): Boolean =
        names.size >= ancestor.names.size && names.take(ancestor.names.size) == ancestor.names

    private enum class CreateType { Folder, Note }

    private companion object {
        const val STATE_FOLDER = "current_folder"
    }
}
