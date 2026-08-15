package com.mlevngr.inknote

import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
        adapter = NoteLibraryAdapter(this, ::openEntry)
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
        val entries = runCatching { library.list(currentFolder) }.getOrElse {
            currentFolder = ""
            library.list("")
        }
        adapter.submit(entries)
        emptyView.visibility = if (entries.isEmpty()) android.view.View.VISIBLE
        else android.view.View.GONE
        toolbar.title = currentFolder.substringAfterLast('/').ifBlank { getString(R.string.app_name) }
        toolbar.navigationIcon = if (currentFolder.isBlank()) null
        else getDrawable(R.drawable.ic_arrow_back_24)
        toolbar.navigationContentDescription = getString(R.string.go_up)
    }

    private fun openEntry(entry: NoteLibrary.Entry) {
        when (entry.type) {
            NoteLibrary.EntryType.Folder -> {
                currentFolder = entry.relativePath
                refresh()
            }
            NoteLibrary.EntryType.Note -> startActivity(EditorActivity.intent(this, entry.relativePath))
        }
    }

    private fun navigateUp() {
        currentFolder = library.parentOf(currentFolder) ?: ""
        refresh()
    }

    private fun showCreateDialog(type: CreateType) {
        val input = EditText(this).apply {
            hint = getString(if (type == CreateType.Folder) R.string.folder_name else R.string.note_name)
            setSingleLine()
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, 0)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(if (type == CreateType.Folder) R.string.create_folder else R.string.create_note)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.create) { _, _ ->
                runCatching {
                    when (type) {
                        CreateType.Folder -> library.createFolder(currentFolder, input.text.toString())
                        CreateType.Note -> library.createNote(currentFolder, input.text.toString())
                    }
                }.onSuccess { entry ->
                    refresh()
                    if (entry.type == NoteLibrary.EntryType.Note) openEntry(entry)
                }.onFailure {
                    Toast.makeText(this, it.message ?: getString(R.string.create_failed), Toast.LENGTH_LONG).show()
                }
            }
            .show()
        input.requestFocus()
    }

    private enum class CreateType { Folder, Note }

    private companion object {
        const val STATE_FOLDER = "current_folder"
    }
}
