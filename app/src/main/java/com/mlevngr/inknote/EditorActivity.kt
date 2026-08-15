package com.mlevngr.inknote

import android.net.Uri
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Rect
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.getSystemService
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.mlevngr.inknote.assets.NoteWorkspace
import com.mlevngr.inknote.library.NoteLibrary
import com.mlevngr.inknote.library.NoteLibrary.FolderLocation
import com.mlevngr.inknote.markdown.MarkdownDocument
import com.mlevngr.inknote.ui.HybridNoteAdapter
import com.mlevngr.inknote.ui.HybridRowFactory
import com.mlevngr.inknote.ui.PreviewRowFactory
import com.mlevngr.inknote.ui.SystemBarInsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class EditorActivity : AppCompatActivity() {
    private lateinit var library: NoteLibrary
    private lateinit var folderLocation: FolderLocation
    private lateinit var workspace: NoteWorkspace
    private lateinit var document: MarkdownDocument
    private lateinit var noteAdapter: HybridNoteAdapter
    private lateinit var rowFactory: HybridRowFactory
    private lateinit var recyclerView: RecyclerView
    private lateinit var modeButton: AppCompatImageButton
    private lateinit var titleInput: TextInputEditText
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val renderRevision = AtomicInteger()
    private var mode = EditorMode.Read
    private var activeLine: Int? = null
    private var lastActiveLine = 0
    private var saveTask: Runnable? = null
    private var noteName = ""
    private val workspaceLock = Any()

    private val openAsset = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::importAsset)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)
        SystemBarInsets.install(
            findViewById(R.id.app_root),
            avoidIme = true,
            onInsetsChanged = {
                if (::recyclerView.isInitialized && ::noteAdapter.isInitialized) {
                    ensureActiveEditorVisible()
                }
            }
        )

        noteName = intent.getStringExtra(EXTRA_NOTE_NAME)
            ?.takeIf(String::isNotBlank)
            ?: run {
                finish()
                return
            }
        folderLocation = FolderLocation(
            intent.getStringArrayListExtra(EXTRA_FOLDER_NAMES).orEmpty().toList()
        )
        library = NoteLibrary(this)
        val initialState = runCatching {
            NoteWorkspace(this, folderLocation, noteName).let {
                it to MarkdownDocument.parse(it.load(DEFAULT_NOTE))
            }
        }.getOrElse {
            Toast.makeText(
                this,
                getString(R.string.open_failed, it.message ?: getString(R.string.unknown_error)),
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }
        workspace = initialState.first
        document = initialState.second
        rowFactory = HybridRowFactory(PreviewRowFactory(workspace))
        noteAdapter = HybridNoteAdapter(
            context = this,
            onActivate = ::activateLine,
            onLongActivate = ::enterEditModeAt,
            onLineChanged = ::updateLine,
            onSplitLine = ::splitLine,
            onMultilineInput = ::replaceLineFromEditor,
            onMergeWithPrevious = ::mergeWithPrevious,
            onDeleteAsset = ::confirmDeleteAsset
        )

        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            title = ""
            setNavigationIcon(R.drawable.ic_arrow_back_24)
            navigationContentDescription = getString(R.string.back_to_library)
            setNavigationOnClickListener { finish() }
        }
        titleInput = findViewById<TextInputEditText>(R.id.note_title).apply {
            setText(noteName)
            setSelection(text?.length ?: 0)
            setOnLongClickListener {
                if (mode == EditorMode.Read) {
                    enterTitleEditMode()
                    true
                } else false
            }
            setOnFocusChangeListener { _, hasFocus ->
                isCursorVisible = hasFocus && mode == EditorMode.Edit
                if (!hasFocus && ::workspace.isInitialized) commitTitleRename()
            }
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    clearFocus()
                    enterEditModeAt(lastActiveLine)
                    true
                } else false
            }
        }
        recyclerView = findViewById<RecyclerView>(R.id.note_content).apply {
            layoutManager = LinearLayoutManager(this@EditorActivity)
            adapter = noteAdapter
            itemAnimator = null
        }
        modeButton = findViewById<AppCompatImageButton>(R.id.toggle_mode).also {
            it.setOnClickListener { toggleMode() }
        }
        findViewById<AppCompatImageButton>(R.id.insert_asset).setOnClickListener {
            openAsset.launch(arrayOf("*/*"))
        }
        updateModeButton()
        updateTitleInteraction()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (mode == EditorMode.Edit) {
                    enterReadMode()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        refreshRows()
    }

    private fun toggleMode() {
        if (mode == EditorMode.Read) enterEditMode() else enterReadMode()
    }

    private fun enterEditMode() {
        enterEditModeAt(lastActiveLine)
    }

    private fun enterEditModeAt(lineIndex: Int) {
        mode = EditorMode.Edit
        activeLine = lineIndex.coerceIn(0, document.size - 1)
        lastActiveLine = activeLine ?: 0
        updateModeButton()
        updateTitleInteraction()
        refreshRows(requestFocus = true)
    }

    private fun enterTitleEditMode() {
        mode = EditorMode.Edit
        activeLine = null
        updateModeButton()
        updateTitleInteraction()
        refreshRows()
        titleInput.post {
            titleInput.requestFocus()
            titleInput.setSelection(titleInput.text?.length ?: 0)
            getSystemService<InputMethodManager>()
                ?.showSoftInput(titleInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun enterReadMode() {
        mode = EditorMode.Read
        activeLine?.let { lastActiveLine = it }
        activeLine = null
        titleInput.clearFocus()
        getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(recyclerView.windowToken, 0)
        recyclerView.clearFocus()
        updateModeButton()
        updateTitleInteraction()
        refreshRows()
        scheduleSave()
    }

    private fun updateModeButton() {
        val reading = mode == EditorMode.Read
        modeButton.setImageResource(if (reading) R.drawable.ic_edit_24 else R.drawable.ic_read_mode_24)
        modeButton.contentDescription = getString(
            if (reading) R.string.switch_to_edit_mode else R.string.switch_to_read_mode
        )
    }

    private fun updateTitleInteraction() {
        val editing = mode == EditorMode.Edit
        titleInput.isFocusable = editing
        titleInput.isFocusableInTouchMode = editing
        titleInput.isCursorVisible = editing && titleInput.hasFocus()
    }

    private fun commitTitleRename(): Boolean {
        val requested = titleInput.text?.toString().orEmpty()
        if (requested == noteName) return true

        saveTask?.let(main::removeCallbacks)
        saveTask = null
        return runCatching {
            synchronized(workspaceLock) {
                workspace.save(document.markdown())
                val renamed = library.renameNote(folderLocation, noteName, requested)
                noteName = renamed.name
                workspace = NoteWorkspace(this, folderLocation, noteName)
                rowFactory = HybridRowFactory(PreviewRowFactory(workspace))
            }
            if (titleInput.text?.toString() != noteName) {
                titleInput.setText(noteName)
                titleInput.setSelection(titleInput.text?.length ?: 0)
            }
            refreshRows()
            true
        }.getOrElse { error ->
            titleInput.setText(noteName)
            titleInput.setSelection(titleInput.text?.length ?: 0)
            Toast.makeText(
                this,
                error.message ?: getString(R.string.rename_failed),
                Toast.LENGTH_LONG
            ).show()
            false
        }
    }

    private fun activateLine(index: Int) {
        if (mode != EditorMode.Edit || activeLine == index) return
        activeLine = index
        lastActiveLine = index
        refreshRows(requestFocus = true)
    }

    private fun updateLine(index: Int, source: String) {
        if (index !in 0 until document.size) return
        document.update(index, source)
        scheduleSave()
        ensureActiveEditorVisible()
    }

    private fun splitLine(index: Int, cursor: Int) {
        if (mode != EditorMode.Edit || index !in 0 until document.size) return
        activeLine = document.splitLine(index, cursor)
        lastActiveLine = activeLine ?: index
        refreshRows(requestFocus = true, cursorPosition = 0)
        scheduleSave()
    }

    private fun mergeWithPrevious(index: Int): Boolean {
        if (mode != EditorMode.Edit) return false
        val cursor = document.mergeWithPrevious(index) ?: return false
        activeLine = index - 1
        lastActiveLine = activeLine ?: 0
        refreshRows(requestFocus = true, cursorPosition = cursor)
        scheduleSave()
        return true
    }

    private fun replaceLineFromEditor(index: Int, source: String, cursor: Int) {
        if (mode != EditorMode.Edit || index !in 0 until document.size) return
        val normalized = source.replace("\r\n", "\n").replace('\r', '\n')
        val replacement = normalized.split('\n', ignoreCase = false, limit = Int.MAX_VALUE)
        if (replacement.size < 2) return
        document.replaceLine(index, replacement)

        val safeCursor = cursor.coerceIn(0, normalized.length)
        val beforeCursor = normalized.substring(0, safeCursor)
        val relativeLine = beforeCursor.count { it == '\n' }
        val cursorInLine = beforeCursor.substringAfterLast('\n').length
        activeLine = index + relativeLine
        lastActiveLine = activeLine ?: index
        refreshRows(requestFocus = true, cursorPosition = cursorInLine)
        scheduleSave()
    }

    private fun refreshRows(requestFocus: Boolean = false, cursorPosition: Int? = null) {
        val revision = renderRevision.incrementAndGet()
        val lines = document.snapshot()
        val active = activeLine
        val editing = mode == EditorMode.Edit
        io.execute {
            val rows = rowFactory.create(lines, active)
            main.post {
                if (!isDestroyed && revision == renderRevision.get()) {
                    noteAdapter.submit(
                        rows,
                        editing,
                        active.takeIf { requestFocus },
                        cursorPosition
                    )
                    if (editing) ensureActiveEditorVisible()
                }
            }
        }
    }

    private fun ensureActiveEditorVisible(retry: Boolean = true) {
        val line = activeLine ?: return
        val position = noteAdapter.positionOfLine(line)
        if (position < 0) return
        recyclerView.post {
            val holder = recyclerView.findViewHolderForAdapterPosition(position)
            if (holder == null) {
                if (retry) {
                    recyclerView.scrollToPosition(position)
                    recyclerView.post { ensureActiveEditorVisible(retry = false) }
                }
                return@post
            }
            val view = holder.itemView
            view.requestRectangleOnScreen(
                Rect(0, 0, view.width, view.height + dp(24)),
                false
            )
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun scheduleSave() {
        saveTask?.let(main::removeCallbacks)
        val markdown = document.markdown()
        saveTask = Runnable {
            io.execute { synchronized(workspaceLock) { workspace.save(markdown) } }
        }.also {
            main.postDelayed(it, SAVE_DELAY_MS)
        }
    }

    private fun importAsset(uri: Uri) {
        io.execute {
            val result = runCatching {
                synchronized(workspaceLock) { workspace.import(contentResolver, uri) }
            }
            main.post {
                result.onSuccess { asset ->
                    document.insertAfter(activeLine, asset.markdown())
                    refreshRows()
                    scheduleSave()
                }.onFailure {
                    Toast.makeText(
                        this,
                        getString(R.string.import_failed, it.message ?: "unknown error"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun confirmDeleteAsset(lineIndex: Int, file: java.io.File) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.remove_inserted_file)
            .setMessage(getString(R.string.remove_inserted_file_confirmation, file.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> deleteAsset(lineIndex, file) }
            .show()
    }

    private fun deleteAsset(lineIndex: Int, file: java.io.File) {
        if (lineIndex !in 0 until document.size) return
        document.removeLine(lineIndex)
        activeLine = activeLine?.let { active ->
            when {
                active > lineIndex -> active - 1
                active == lineIndex -> lineIndex.coerceAtMost(document.size - 1)
                else -> active
            }
        }
        lastActiveLine = lastActiveLine.coerceAtMost(document.size - 1)
        val remainingMarkdown = document.markdown()
        io.execute {
            synchronized(workspaceLock) {
                runCatching { workspace.deleteAssetIfUnreferenced(file, remainingMarkdown) }
            }
        }
        refreshRows()
        scheduleSave()
    }

    override fun onStop() {
        super.onStop()
        if (!::document.isInitialized || !::workspace.isInitialized) return
        commitTitleRename()
        val markdown = document.markdown()
        io.execute { synchronized(workspaceLock) { workspace.save(markdown) } }
    }

    override fun onDestroy() {
        saveTask?.let(main::removeCallbacks)
        if (::noteAdapter.isInitialized) noteAdapter.close()
        io.shutdown()
        super.onDestroy()
    }

    private enum class EditorMode { Read, Edit }

    companion object {
        private const val SAVE_DELAY_MS = 350L
        private const val EXTRA_FOLDER_NAMES = "folder_names"
        private const val EXTRA_NOTE_NAME = "note_name"

        fun intent(
            context: android.content.Context,
            folderLocation: FolderLocation,
            noteName: String
        ): Intent =
            Intent(context, EditorActivity::class.java)
                .putStringArrayListExtra(EXTRA_FOLDER_NAMES, ArrayList(folderLocation.names))
                .putExtra(EXTRA_NOTE_NAME, noteName)

        val DEFAULT_NOTE = """
            阅读模式没有光标，所有内容都是渲染结果。

            点击右上角编辑图标进入编辑模式；只有光标所在行显示 Markdown 源码。

            使用链接形状的插入按钮选择图片、PDF 或其他文件，文件会复制到笔记内部。
        """.trimIndent()
    }
}
