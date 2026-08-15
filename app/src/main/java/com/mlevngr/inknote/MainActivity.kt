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
import com.mlevngr.inknote.ui.NoteLibraryAdapter
import com.mlevngr.inknote.ui.SystemBarInsets

class MainActivity : AppCompatActivity() {
    private lateinit var library: NoteLibrary
    private lateinit var adapter: NoteLibraryAdapter
    private lateinit var toolbar: MaterialToolbar
    private lateinit var emptyView: TextView
    private var currentFolder = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library)
        SystemBarInsets.install(findViewById(R.id.library_root))

        library = NoteLibrary(this)
        currentFolder = savedInstanceState?.getString(STATE_FOLDER).orEmpty()
        adapter = NoteLibraryAdapter(this, ::openEntry, ::showNoteActions)
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
                if (currentFolder.isNotBlank()) navigateUp()
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
        outState.putString(STATE_FOLDER, currentFolder)
        super.onSaveInstanceState(outState)
    }

    private fun refresh() {
        val entries = runCatching { library.list(currentFolder) }.getOrElse { error ->
            currentFolder = ""
            Toast.makeText(
                this,
                getString(R.string.open_failed, error.message ?: getString(R.string.unknown_error)),
                Toast.LENGTH_LONG
            ).show()
            runCatching { library.list("") }.getOrDefault(emptyList())
        }
        adapter.submit(entries)
        emptyView.visibility = if (entries.isEmpty()) android.view.View.VISIBLE
        else android.view.View.GONE
        toolbar.title = library.displayPath(currentFolder)
            .substringAfterLast(" / ")
            .ifBlank { getString(R.string.app_name) }
        if (currentFolder.isBlank()) toolbar.navigationIcon = null
        else toolbar.setNavigationIcon(R.drawable.ic_arrow_back_24)
        toolbar.navigationContentDescription = getString(R.string.go_up)
    }

    private fun openEntry(entry: NoteLibrary.Entry) {
        runCatching {
            when (entry.type) {
                NoteLibrary.EntryType.Folder -> {
                    library.list(entry.relativePath)
                    currentFolder = entry.relativePath
                    refresh()
                }
                NoteLibrary.EntryType.Note -> {
                    library.requireNote(entry.relativePath)
                    startActivity(EditorActivity.intent(this, entry.relativePath))
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
        currentFolder = library.parentOf(currentFolder) ?: ""
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

    private fun showNoteActions(entry: NoteLibrary.Entry) {
        if (entry.type != NoteLibrary.EntryType.Note) return
        val actions = arrayOf(getString(R.string.move_note), getString(R.string.delete_note))
        MaterialAlertDialogBuilder(this)
            .setTitle(entry.name)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> showMoveDialog(entry)
                    1 -> showDeleteConfirmation(entry)
                }
            }
            .show()
    }

    private fun showDeleteConfirmation(note: NoteLibrary.Entry) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_note)
            .setMessage(getString(R.string.delete_note_confirmation, note.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                runCatching { library.deleteNote(note.relativePath) }
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

    private fun showMoveDialog(note: NoteLibrary.Entry) {
        val destinations = listOf<String?>(null) + library.listFolders().map { it.relativePath }
        val labels = destinations.map { path ->
            path?.let(library::displayPath) ?: getString(R.string.library_root)
        }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.move_to)
            .setItems(labels) { _, which ->
                val target = destinations[which].orEmpty()
                runCatching { library.moveNote(note.relativePath, target) }
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

    private enum class CreateType { Folder, Note }

    private companion object {
        const val STATE_FOLDER = "current_folder"
    }
}
