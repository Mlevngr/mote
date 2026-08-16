package com.mlevngr.inknote

import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.graphics.ColorUtils
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.mlevngr.inknote.appearance.AppTheme
import com.mlevngr.inknote.appearance.AppearancePreferences
import com.mlevngr.inknote.appearance.LibraryLayoutMode
import com.mlevngr.inknote.appearance.NotePreviewMode
import com.mlevngr.inknote.appearance.ThemePalette
import com.mlevngr.inknote.library.FolderColor
import com.mlevngr.inknote.library.NoteLibrary
import com.mlevngr.inknote.library.NoteLibrary.FolderLocation
import com.mlevngr.inknote.library.TrashPreferences
import com.mlevngr.inknote.appearance.ThemeColors
import com.mlevngr.inknote.ui.NoteLibraryAdapter
import com.mlevngr.inknote.ui.SystemBarInsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : AppCompatActivity() {
    private lateinit var library: NoteLibrary
    private lateinit var adapter: NoteLibraryAdapter
    private lateinit var appearance: AppearancePreferences
    private lateinit var trashPreferences: TrashPreferences
    private lateinit var toolbar: MaterialToolbar
    private lateinit var drawer: DrawerLayout
    private lateinit var navigateUpButton: AppCompatImageButton
    private lateinit var emptyView: TextView
    private lateinit var recyclerView: RecyclerView
    private var currentFolder = FolderLocation.Root
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val refreshRevision = AtomicInteger()

    override fun onCreate(savedInstanceState: Bundle?) {
        appearance = AppearancePreferences(this)
        appearance.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library)
        SystemBarInsets.install(findViewById(R.id.library_root))

        library = NoteLibrary(this)
        trashPreferences = TrashPreferences(this)
        currentFolder = runCatching {
            savedInstanceState
                ?.getStringArrayList(STATE_FOLDER)
                ?.let { FolderLocation(it.toList()) }
        }.getOrNull() ?: FolderLocation.Root
        adapter = NoteLibraryAdapter(this, ::openEntry, ::showEntryActions)
        toolbar = findViewById(R.id.library_toolbar)
        drawer = findViewById(R.id.library_root)
        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        findViewById<AppCompatImageButton>(R.id.open_drawer).setOnClickListener {
            drawer.openDrawer(GravityCompat.START)
        }
        navigateUpButton = findViewById<AppCompatImageButton>(R.id.navigate_up).also { button ->
            button.setOnClickListener { navigateUp() }
        }
        emptyView = findViewById(R.id.empty_library)
        recyclerView = findViewById<RecyclerView>(R.id.library_list).apply {
            adapter = this@MainActivity.adapter
            itemAnimator = null
        }
        configureLibraryLayout()
        findViewById<NavigationView>(R.id.library_navigation)
            .setNavigationItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.navigation_appearance -> showAppearanceDialog()
                    R.id.navigation_webdav ->
                        startActivity(Intent(this, WebDavSettingsActivity::class.java))
                    R.id.navigation_plugins ->
                        startActivity(Intent(this, PluginManagerActivity::class.java))
                    R.id.navigation_trash ->
                        startActivity(Intent(this, TrashActivity::class.java))
                }
                drawer.closeDrawer(GravityCompat.START)
                true
            }
        findViewById<AppCompatImageButton>(R.id.create_folder).setOnClickListener {
            showCreateDialog(CreateType.Folder)
        }
        findViewById<AppCompatImageButton>(R.id.create_note).setOnClickListener {
            showCreateDialog(CreateType.Note)
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawer.isDrawerOpen(GravityCompat.START)) {
                    drawer.closeDrawer(GravityCompat.START)
                } else if (currentFolder.names.isNotEmpty()) navigateUp()
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

    override fun onDestroy() {
        if (::adapter.isInitialized) adapter.close()
        io.shutdownNow()
        super.onDestroy()
    }

    private fun refresh() {
        val requestedFolder = currentFolder
        val revision = refreshRevision.incrementAndGet()
        io.execute {
            library.cleanupExpiredTrash(trashPreferences.retention.durationMillis)
            val requested = runCatching { library.list(requestedFolder) }
            val entries = requested.getOrElse { runCatching { library.list(FolderLocation.Root) }.getOrDefault(emptyList()) }
            main.post {
                if (revision != refreshRevision.get() || isFinishing || isDestroyed) return@post
                requested.exceptionOrNull()?.let { error ->
                    currentFolder = FolderLocation.Root
                    Toast.makeText(
                        this,
                        getString(R.string.open_failed, error.message ?: getString(R.string.unknown_error)),
                        Toast.LENGTH_LONG
                    ).show()
                }
                adapter.submit(entries, appearance.libraryLayout, appearance.notePreview)
                emptyView.visibility = if (entries.isEmpty()) android.view.View.VISIBLE
                else android.view.View.GONE
                toolbar.title = currentFolder.title ?: getString(R.string.app_name)
                navigateUpButton.visibility = if (currentFolder.names.isEmpty()) {
                    android.view.View.GONE
                } else android.view.View.VISIBLE
            }
        }
    }

    private fun configureLibraryLayout() {
        recyclerView.layoutManager = GridLayoutManager(this, 2).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int = adapter.spanSize(position)
            }
        }
        recyclerView.setPadding(
            if (appearance.libraryLayout == LibraryLayoutMode.List) 4.dp else 8.dp,
            4.dp,
            if (appearance.libraryLayout == LibraryLayoutMode.List) 4.dp else 8.dp,
            24.dp
        )
    }

    private fun showAppearanceDialog() {
        val items = arrayOf(
            getString(R.string.theme_setting),
            getString(R.string.library_layout_setting),
            getString(R.string.note_preview_setting)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.appearance)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showThemeDialog()
                    1 -> showLayoutDialog()
                    2 -> showPreviewDialog()
                }
            }
            .show()
    }

    private fun showThemeDialog() {
        val themes = AppTheme.entries
        val labels = arrayOf(
            getString(R.string.theme_inknote),
            getString(R.string.theme_catppuccin),
            getString(R.string.theme_tokyo_night),
            getString(R.string.theme_minimal)
        )
        val night = isNightMode()
        val surface = ThemeColors.resolve(this, com.google.android.material.R.attr.colorSurfaceContainer)
        val outline = ThemeColors.resolve(this, com.google.android.material.R.attr.colorOutline)
        val currentPrimary = ThemeColors.resolve(this, androidx.appcompat.R.attr.colorPrimary)
        val options = mutableListOf<Pair<MaterialCardView, AppTheme>>()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp, 8.dp, 16.dp, 12.dp)
        }
        themes.forEachIndexed { index, theme ->
            val preview = ThemePalette.preview(theme, night)
            val selected = theme == appearance.theme
            val rowContent = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                orientation = LinearLayout.HORIZONTAL
                setPadding(18.dp, 0, 14.dp, 0)
                addView(TextView(this@MainActivity).apply {
                    text = labels[index]
                    textSize = 16f
                    setTextColor(preview.accent)
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                listOf(preview.background, preview.surface, preview.accent).forEach { color ->
                    addView(FrameLayout(this@MainActivity).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(color)
                            setStroke(1.dp, ColorUtils.setAlphaComponent(outline, 140))
                        }
                    }, LinearLayout.LayoutParams(20.dp, 20.dp).apply {
                        marginStart = 6.dp
                    })
                }
                addView(ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.ic_check_24)
                    imageTintList = ColorStateList.valueOf(currentPrimary)
                    visibility = if (selected) android.view.View.VISIBLE else android.view.View.INVISIBLE
                }, LinearLayout.LayoutParams(24.dp, 24.dp).apply {
                    marginStart = 12.dp
                })
            }
            val card = MaterialCardView(this).apply {
                radius = 14.dp.toFloat()
                cardElevation = 0f
                setCardBackgroundColor(surface)
                strokeWidth = if (selected) 2.dp else 1.dp
                setStrokeColor(if (selected) currentPrimary else ColorUtils.setAlphaComponent(outline, 110))
                rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(currentPrimary, 28))
                isClickable = true
                isFocusable = true
                contentDescription = labels[index]
                addView(rowContent)
            }
            content.addView(card, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                60.dp
            ).apply {
                topMargin = 4.dp
                bottomMargin = 4.dp
            })
            options += card to theme
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.theme_setting)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        options.forEach { (view, theme) ->
            view.setOnClickListener {
                if (theme != appearance.theme) {
                    appearance.theme = theme
                    dialog.dismiss()
                    recreate()
                } else {
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun showLayoutDialog() {
        val modes = LibraryLayoutMode.entries
        val labels = arrayOf(
            getString(R.string.layout_samsung),
            getString(R.string.layout_list),
            getString(R.string.layout_grid)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.library_layout_setting)
            .setSingleChoiceItems(labels, modes.indexOf(appearance.libraryLayout)) { dialog, which ->
                appearance.libraryLayout = modes[which]
                configureLibraryLayout()
                dialog.dismiss()
                refresh()
            }
            .show()
    }

    private fun showPreviewDialog() {
        val modes = NotePreviewMode.entries
        val labels = arrayOf(
            getString(R.string.preview_rendered_page),
            getString(R.string.preview_summary),
            getString(R.string.preview_title_only)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.note_preview_setting)
            .setSingleChoiceItems(labels, modes.indexOf(appearance.notePreview)) { dialog, which ->
                appearance.notePreview = modes[which]
                dialog.dismiss()
                refresh()
            }
            .show()
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
                getString(R.string.folder_color),
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
                        1 -> showFolderColorDialog(entry)
                        2 -> showMoveFolderDialog(entry)
                        3 -> showDeleteFolderConfirmation(entry)
                    }
                }
            }
            .show()
    }

    private fun showFolderColorDialog(folder: NoteLibrary.Entry) {
        val colors = FolderColor.entries
        val descriptions = arrayOf(
            getString(R.string.folder_color_blue),
            getString(R.string.folder_color_indigo),
            getString(R.string.folder_color_purple),
            getString(R.string.folder_color_pink),
            getString(R.string.folder_color_red),
            getString(R.string.folder_color_orange),
            getString(R.string.folder_color_amber),
            getString(R.string.folder_color_green),
            getString(R.string.folder_color_teal),
            getString(R.string.folder_color_cyan),
            getString(R.string.folder_color_brown),
            getString(R.string.folder_color_gray)
        )
        val primary = ThemeColors.resolve(this, androidx.appcompat.R.attr.colorPrimary)
        val surface = ThemeColors.resolve(this, com.google.android.material.R.attr.colorSurfaceContainer)
        val outline = ThemeColors.resolve(this, com.google.android.material.R.attr.colorOutline)
        val textPrimary = ThemeColors.resolve(this, R.attr.inkNoteTextPrimary)
        val grid = GridLayout(this).apply {
            columnCount = 4
            alignmentMode = GridLayout.ALIGN_BOUNDS
            setPadding(16.dp, 10.dp, 16.dp, 12.dp)
        }
        colors.forEachIndexed { index, color ->
            val selected = color == folder.folderColor
            val fill = folderColorValue(color)
            val swatch = FrameLayout(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(fill)
                    setStroke(1.dp, ColorUtils.setAlphaComponent(textPrimary, 32))
                }
                if (selected) {
                    addView(ImageView(this@MainActivity).apply {
                        setImageResource(R.drawable.ic_check_24)
                        imageTintList = ColorStateList.valueOf(
                            if (ColorUtils.calculateLuminance(fill) > 0.45) Color.BLACK else Color.WHITE
                        )
                        contentDescription = null
                    }, FrameLayout.LayoutParams(22.dp, 22.dp, Gravity.CENTER))
                }
            }
            val card = MaterialCardView(this).apply {
                radius = 20.dp.toFloat()
                cardElevation = 0f
                setCardBackgroundColor(surface)
                strokeWidth = if (selected) 2.dp else 1.dp
                setStrokeColor(if (selected) primary else ColorUtils.setAlphaComponent(outline, 130))
                rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(primary, 30))
                isClickable = true
                isFocusable = true
                tag = color
                contentDescription = getString(R.string.folder_color_option, descriptions[index])
                addView(swatch, FrameLayout.LayoutParams(42.dp, 42.dp, Gravity.CENTER))
            }
            grid.addView(card, GridLayout.LayoutParams().apply {
                width = 64.dp
                height = 64.dp
                setMargins(4.dp, 4.dp, 4.dp, 4.dp)
            })
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.folder_color)
            .setView(grid)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        grid.children().forEach { child ->
            child.setOnClickListener {
                val color = child.tag as FolderColor
                runCatching { library.setFolderColor(currentFolder, folder.name, color) }
                    .onSuccess { refresh() }
                    .onFailure { Toast.makeText(this, it.message, Toast.LENGTH_LONG).show() }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun GridLayout.children(): List<android.view.View> =
        List(childCount) { getChildAt(it) }

    private fun folderColorValue(folderColor: FolderColor): Int {
        return ThemePalette.folderColor(appearance.theme, folderColor, isNightMode())
    }

    private fun isNightMode(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

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
            .setPositiveButton(R.string.move_to_trash) { _, _ ->
                runCatching { library.moveNoteToTrash(currentFolder, note.name) }
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
            .setPositiveButton(R.string.move_to_trash) { _, _ ->
                runCatching { library.moveFolderToTrash(currentFolder, folder.name) }
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

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    private companion object {
        const val STATE_FOLDER = "current_folder"
    }
}
