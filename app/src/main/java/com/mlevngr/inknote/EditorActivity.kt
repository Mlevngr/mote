package com.mlevngr.inknote

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Menu
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.getSystemService
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mlevngr.inknote.assets.NoteWorkspace
import com.mlevngr.inknote.appearance.AppearancePreferences
import com.mlevngr.inknote.library.NoteLibrary
import com.mlevngr.inknote.library.NoteLibrary.FolderLocation
import com.mlevngr.inknote.markdown.MarkdownDocument
import com.mlevngr.inknote.markdown.MarkdownBlockStyle
import com.mlevngr.inknote.markdown.MarkdownEditResult
import com.mlevngr.inknote.markdown.MarkdownEditing
import com.mlevngr.inknote.markdown.MarkdownHistory
import com.mlevngr.inknote.markdown.MarkdownHistoryKind
import com.mlevngr.inknote.markdown.MarkdownHistoryState
import com.mlevngr.inknote.ui.HybridNoteAdapter
import com.mlevngr.inknote.ui.HybridRowFactory
import com.mlevngr.inknote.ui.ImeBackTextInputEditText
import com.mlevngr.inknote.ui.PreviewRowFactory
import com.mlevngr.inknote.ui.SystemBarInsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.io.File

class EditorActivity : AppCompatActivity() {
    private lateinit var library: NoteLibrary
    private lateinit var folderLocation: FolderLocation
    private lateinit var workspace: NoteWorkspace
    private lateinit var document: MarkdownDocument
    private lateinit var noteAdapter: HybridNoteAdapter
    private lateinit var rowFactory: HybridRowFactory
    private lateinit var recyclerView: RecyclerView
    private lateinit var modeButton: AppCompatImageButton
    private lateinit var undoButton: AppCompatImageButton
    private lateinit var redoButton: AppCompatImageButton
    private lateinit var titleInput: ImeBackTextInputEditText
    private lateinit var markdownToolbar: View
    private lateinit var headingButton: MaterialButton
    private lateinit var history: MarkdownHistory
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val renderRevision = AtomicInteger()
    private var mode = EditorMode.Read
    private var activeLine: Int? = null
    private var lastActiveLine = 0
    private var saveTask: Runnable? = null
    private var noteName = ""
    private var pendingAssetTransfer: AssetTransfer? = null
    private val workspaceLock = Any()

    private val openAsset = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::importAsset)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppearancePreferences(this).applyTheme(this)
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
        history = MarkdownHistory(
            MarkdownHistoryState(document.markdown(), 0, 0, 0)
        )
        rowFactory = HybridRowFactory(PreviewRowFactory(workspace))
        noteAdapter = HybridNoteAdapter(
            context = this,
            onActivate = ::activateLine,
            onPreviewDoubleTap = ::enterEditModeAt,
            onLineChanged = ::updateLine,
            onSplitLine = ::splitLine,
            onMultilineInput = ::replaceLineFromEditor,
            onMergeWithPrevious = ::mergeWithPrevious,
            onAssetActions = ::showAssetActions,
            onPasteAt = ::showPasteAt,
            onPasteAtBoundary = ::showPasteAtBoundary,
            onAppendAtEnd = ::appendLineAtEnd,
            onBackFromIme = ::handleBackNavigation
        )

        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            title = ""
            setNavigationIcon(R.drawable.ic_arrow_back_24)
            navigationContentDescription = getString(R.string.back_to_library)
            setNavigationOnClickListener { handleBackNavigation() }
        }
        titleInput = findViewById<ImeBackTextInputEditText>(R.id.note_title).apply {
            onImeBack = ::handleBackNavigation
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
        undoButton = findViewById<AppCompatImageButton>(R.id.undo).also {
            it.setOnClickListener { undo() }
        }
        redoButton = findViewById<AppCompatImageButton>(R.id.redo).also {
            it.setOnClickListener { redo() }
        }
        findViewById<AppCompatImageButton>(R.id.insert_asset).setOnClickListener {
            openAsset.launch(arrayOf("*/*"))
        }
        setupMarkdownToolbar()
        updateModeButton()
        updateTitleInteraction()
        updateMarkdownToolbar()
        updateHistoryButtons()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleBackNavigation()
        })
        refreshRows()
    }

    private fun toggleMode() {
        if (mode == EditorMode.Read) enterEditMode() else enterReadMode()
    }

    private fun handleBackNavigation() {
        if (mode == EditorMode.Edit) enterReadMode() else finish()
    }

    private fun enterEditMode() {
        enterEditModeAt(lastActiveLine)
    }

    private fun enterEditModeAt(lineIndex: Int) {
        mode = EditorMode.Edit
        activeLine = lineIndex.coerceIn(0, document.size - 1)
        lastActiveLine = activeLine ?: 0
        updateHistoryFocus()
        updateModeButton()
        updateTitleInteraction()
        updateMarkdownToolbar()
        updateHistoryButtons()
        refreshRows(requestFocus = true)
    }

    private fun enterTitleEditMode() {
        mode = EditorMode.Edit
        activeLine = null
        history.breakGroup()
        updateModeButton()
        updateTitleInteraction()
        updateMarkdownToolbar()
        updateHistoryButtons()
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
        history.breakGroup()
        titleInput.clearFocus()
        getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(recyclerView.windowToken, 0)
        recyclerView.clearFocus()
        updateModeButton()
        updateTitleInteraction()
        updateMarkdownToolbar()
        updateHistoryButtons()
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

    private fun setupMarkdownToolbar() {
        markdownToolbar = findViewById(R.id.markdown_toolbar)
        headingButton = findViewById<MaterialButton>(R.id.markdown_heading).also { button ->
            button.setOnClickListener { showHeadingMenu() }
        }
        findViewById<View>(R.id.markdown_bold).setOnClickListener {
            applyMarkdownEdit(MarkdownEditing::bold)
        }
        findViewById<View>(R.id.markdown_italic).setOnClickListener {
            applyMarkdownEdit(MarkdownEditing::italic)
        }
        findViewById<View>(R.id.markdown_strikethrough).setOnClickListener {
            applyMarkdownEdit(MarkdownEditing::strikethrough)
        }
        findViewById<View>(R.id.markdown_task).setOnClickListener {
            applyBlockStyle(MarkdownBlockStyle.Task)
        }
        findViewById<View>(R.id.markdown_bullet_list).setOnClickListener {
            applyBlockStyle(MarkdownBlockStyle.Bullet)
        }
        findViewById<View>(R.id.markdown_numbered_list).setOnClickListener {
            toggleOrderedList()
        }
        findViewById<View>(R.id.markdown_quote).setOnClickListener {
            applyBlockStyle(MarkdownBlockStyle.Quote)
        }
        findViewById<View>(R.id.markdown_inline_code).setOnClickListener {
            applyMarkdownEdit(MarkdownEditing::inlineCode)
        }
        findViewById<View>(R.id.markdown_link).setOnClickListener {
            val placeholder = getString(R.string.markdown_link_placeholder)
            applyMarkdownEdit { source, start, end ->
                MarkdownEditing.link(source, start, end, placeholder)
            }
        }
    }

    private fun showHeadingMenu() {
        val current = activeLine?.let { MarkdownEditing.headingLevel(document[it]) } ?: 0
        PopupMenu(this, headingButton).apply {
            menu.add(Menu.NONE, 0, 0, getString(R.string.markdown_body))
            val labels = intArrayOf(
                R.string.markdown_heading_1,
                R.string.markdown_heading_2,
                R.string.markdown_heading_3,
                R.string.markdown_heading_4,
                R.string.markdown_heading_5,
                R.string.markdown_heading_6
            )
            labels.forEachIndexed { index, label ->
                val level = index + 1
                menu.add(Menu.NONE, level, level, "H$level  ${getString(label)}")
            }
            menu.setGroupCheckable(Menu.NONE, true, true)
            menu.findItem(current)?.isChecked = true
            setOnMenuItemClickListener { item ->
                applyMarkdownEdit { source, start, end ->
                    MarkdownEditing.setHeading(source, start, end, item.itemId)
                }
                true
            }
            show()
        }
    }

    private fun applyBlockStyle(style: MarkdownBlockStyle) {
        applyMarkdownEdit { source, start, end ->
            MarkdownEditing.toggleBlock(source, start, end, style)
        }
    }

    private fun toggleOrderedList() {
        val line = activeLine ?: return
        val current = noteAdapter.activeEditState(line) ?: MarkdownEditResult(
            source = document[line],
            selectionStart = document[line].length,
            selectionEnd = document[line].length
        )
        updateHistoryFocus(current)
        val toggled = MarkdownEditing.toggleBlock(
            current.source,
            current.selectionStart,
            current.selectionEnd,
            MarkdownBlockStyle.Ordered
        )
        document.update(line, toggled.source)

        val selection = if (MarkdownEditing.isOrderedLine(toggled.source)) {
            document.renumberOrderedListAt(line)
            MarkdownEditing.adjustSelectionAfterOrderedRenumber(
                toggled.source,
                document[line],
                toggled.selectionStart,
                toggled.selectionEnd
            )
        } else {
            val removedNumber = MarkdownEditing.orderedNumber(current.source)
            val removedIndent = MarkdownEditing.orderedIndent(current.source)
            document.renumberOrderedListAt(line - 1)
            val nextIndent = document.getOrNull(line + 1)?.let(MarkdownEditing::orderedIndent)
            if (nextIndent == removedIndent) {
                document.renumberOrderedListAt(line + 1, startingNumber = removedNumber)
            }
            toggled
        }
        recordHistory(
            MarkdownHistoryKind.Structural,
            line,
            selection.selectionEnd,
            selection.selectionStart
        )
        refreshRows(
            requestFocus = true,
            cursorPosition = selection.selectionEnd,
            selectionStart = selection.selectionStart
        )
        scheduleSave()
    }

    private fun applyMarkdownEdit(
        transform: (String, Int, Int) -> MarkdownEditResult
    ) {
        val line = activeLine ?: return
        updateHistoryFocus()
        noteAdapter.editActiveLine(line, transform)
    }

    private fun updateMarkdownToolbar() {
        val line = activeLine
        val visible = mode == EditorMode.Edit && line != null
        markdownToolbar.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) {
            val level = MarkdownEditing.headingLevel(document[line])
            headingButton.text = if (level == 0) getString(R.string.markdown_body) else "H$level"
        }
    }

    private fun recordHistory(
        kind: MarkdownHistoryKind,
        line: Int?,
        cursor: Int,
        selectionStart: Int = cursor
    ) {
        history.record(
            state = MarkdownHistoryState(
                markdown = document.markdown(),
                activeLine = line,
                selectionStart = selectionStart,
                selectionEnd = cursor
            ),
            kind = kind,
            line = line,
            timestampMillis = SystemClock.uptimeMillis()
        )
        updateHistoryButtons()
    }

    private fun updateHistoryFocus(editorState: MarkdownEditResult? = null) {
        val line = activeLine ?: return
        val editor = editorState ?: noteAdapter.activeEditState(line)
        val cursor = editor?.selectionEnd ?: document[line].length
        history.updateCurrentState(
            MarkdownHistoryState(
                markdown = document.markdown(),
                activeLine = line,
                selectionStart = editor?.selectionStart ?: cursor,
                selectionEnd = cursor
            )
        )
        history.breakGroup()
    }

    private fun undo() {
        history.undo()?.let(::restoreHistoryState)
    }

    private fun redo() {
        history.redo()?.let(::restoreHistoryState)
    }

    private fun restoreHistoryState(state: MarkdownHistoryState) {
        clearAssetTransfer()
        document = MarkdownDocument.parse(state.markdown)
        val line = (state.activeLine ?: lastActiveLine).coerceIn(0, document.size - 1)
        activeLine = line
        lastActiveLine = line
        updateMarkdownToolbar()
        updateHistoryButtons()
        refreshRows(
            requestFocus = mode == EditorMode.Edit,
            cursorPosition = state.selectionEnd,
            selectionStart = state.selectionStart
        )
        scheduleSave()
    }

    private fun updateHistoryButtons() {
        if (!::undoButton.isInitialized || !::redoButton.isInitialized) return
        val visible = mode == EditorMode.Edit && activeLine != null
        undoButton.visibility = if (visible) View.VISIBLE else View.GONE
        redoButton.visibility = if (visible) View.VISIBLE else View.GONE
        undoButton.isEnabled = history.canUndo
        redoButton.isEnabled = history.canRedo
        undoButton.alpha = if (history.canUndo) 1f else DISABLED_ALPHA
        redoButton.alpha = if (history.canRedo) 1f else DISABLED_ALPHA
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
        updateHistoryFocus()
        updateMarkdownToolbar()
        refreshRows(requestFocus = true)
    }

    private fun updateLine(
        index: Int,
        source: String,
        kind: MarkdownHistoryKind,
        selectionStart: Int,
        selectionEnd: Int
    ) {
        if (index !in 0 until document.size) return
        val previous = document[index]
        val deletedNumber = MarkdownEditing.orderedNumber(previous)
        val deletedIndent = MarkdownEditing.orderedIndent(previous)
        document.update(index, source)
        val nextIndent = document.getOrNull(index + 1)?.let(MarkdownEditing::orderedIndent)
        val renumbered = deletedNumber != null && !MarkdownEditing.isOrderedLine(source) &&
            nextIndent == deletedIndent &&
            document.renumberOrderedListAt(index + 1, startingNumber = deletedNumber)
        recordHistory(kind, index, selectionEnd, selectionStart)
        updateMarkdownToolbar()
        if (renumbered) {
            refreshRows(
                requestFocus = true,
                cursorPosition = selectionEnd,
                selectionStart = selectionStart
            )
        }
        scheduleSave()
        ensureActiveEditorVisible()
    }

    private fun splitLine(index: Int, cursor: Int) {
        if (mode != EditorMode.Edit || index !in 0 until document.size) return
        updateHistoryFocus(
            MarkdownEditResult(document[index], cursor, cursor)
        )
        val orderedSplit = MarkdownEditing.splitOrderedLine(document[index], cursor)
        val cursorPosition = if (orderedSplit == null) {
            activeLine = document.splitLine(index, cursor)
            0
        } else {
            document.replaceLine(index, listOf(orderedSplit.currentLine, orderedSplit.nextLine))
            activeLine = index + 1
            document.renumberOrderedListAt(index + 1)
            MarkdownEditing.orderedPrefixLength(document[index + 1]) ?: 0
        }
        lastActiveLine = activeLine ?: index
        recordHistory(MarkdownHistoryKind.Structural, activeLine, cursorPosition)
        updateMarkdownToolbar()
        refreshRows(requestFocus = true, cursorPosition = cursorPosition)
        scheduleSave()
    }

    private fun mergeWithPrevious(index: Int): Boolean {
        if (mode != EditorMode.Edit) return false
        updateHistoryFocus(
            MarkdownEditResult(document[index], 0, 0)
        )
        val deletedLine = document.getOrNull(index)
        val deletedNumber = deletedLine?.let(MarkdownEditing::orderedNumber)
        val deletedIndent = deletedLine?.let(MarkdownEditing::orderedIndent)
        val cursor = document.mergeWithPrevious(index) ?: return false
        if (deletedNumber != null &&
            document.getOrNull(index)?.let(MarkdownEditing::orderedIndent) == deletedIndent
        ) {
            document.renumberOrderedListAt(index, startingNumber = deletedNumber)
        }
        activeLine = index - 1
        lastActiveLine = activeLine ?: 0
        recordHistory(MarkdownHistoryKind.Structural, activeLine, cursor)
        updateMarkdownToolbar()
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
        recordHistory(MarkdownHistoryKind.Structural, activeLine, cursorInLine)
        updateMarkdownToolbar()
        refreshRows(requestFocus = true, cursorPosition = cursorInLine)
        scheduleSave()
    }

    private fun appendLineAtEnd() {
        val lastLine = document.size - 1
        val inserted = !document[lastLine].isBlank()
        val target = if (document[lastLine].isBlank()) {
            lastLine
        } else {
            document.insertAfter(lastLine, "")
        }
        if (inserted) recordHistory(MarkdownHistoryKind.Structural, target, 0)
        enterEditModeAt(target)
        scheduleSave()
    }

    private fun refreshRows(
        requestFocus: Boolean = false,
        cursorPosition: Int? = null,
        selectionStart: Int? = null
    ) {
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
                        cursorPosition,
                        selectionStart
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
                    val inserted = document.insertAfter(activeLine, asset.markdown())
                    recordHistory(MarkdownHistoryKind.Structural, inserted, 0)
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

    private fun showAssetActions(lineIndex: Int, file: File, label: String) {
        val actions = arrayOf(
            getString(R.string.cut_inserted_file),
            getString(R.string.copy_inserted_file),
            getString(R.string.remove_inserted_file)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(label.ifBlank { file.name })
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> stageAssetTransfer(lineIndex, file, label, cut = true)
                    1 -> stageAssetTransfer(lineIndex, file, label, cut = false)
                    2 -> confirmDeleteAsset(lineIndex, file)
                }
            }
            .show()
    }

    private fun stageAssetTransfer(lineIndex: Int, file: File, label: String, cut: Boolean) {
        if (lineIndex !in 0 until document.size) return
        val source = document[lineIndex]
        val assetPath = runCatching {
            file.canonicalFile.relativeTo(workspace.root.canonicalFile).invariantSeparatorsPath
        }.getOrElse { file.name }
        if (mode == EditorMode.Edit) enterReadMode()
        pendingAssetTransfer = AssetTransfer(source, assetPath)
        noteAdapter.setAssetPastePending(true)
        getSystemService<ClipboardManager>()?.setPrimaryClip(
            ClipData.newPlainText(label.ifBlank { file.name }, source)
        )
        if (cut) {
            document.removeLine(lineIndex)
            activeLine = activeLine?.let { active ->
                when {
                    active > lineIndex -> active - 1
                    active == lineIndex -> lineIndex.coerceAtMost(document.size - 1)
                    else -> active
                }
            }
            lastActiveLine = lastActiveLine.coerceAtMost(document.size - 1)
            recordHistory(MarkdownHistoryKind.Structural, activeLine, 0)
            refreshRows()
            scheduleSave()
        }
        Toast.makeText(
            this,
            if (cut) R.string.asset_cut else R.string.asset_copied,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showPasteAt(targetLine: Int?) {
        val clipboardText = clipboardText()
        if (pendingAssetTransfer == null && clipboardText.isNullOrEmpty()) {
            Toast.makeText(this, R.string.nothing_to_paste, Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setItems(arrayOf(getString(R.string.paste))) { _, _ ->
                pasteAt(targetLine, clipboardText)
            }
            .show()
    }

    private fun pasteAt(targetLine: Int?, clipboardText: String?) {
        val stagedTransfer = pendingAssetTransfer
        val transfer = stagedTransfer?.takeIf { pending ->
            clipboardText == null || clipboardText == pending.source
        }
        if (stagedTransfer != null && transfer == null) clearAssetTransfer()
        val target = targetLine?.coerceIn(0, document.size - 1)
        val source = transfer?.source ?: clipboardText.orEmpty()
        if (source.isEmpty()) return
        if (transfer == null) {
            val assetPath = ASSET_EMBED.matchEntire(source.trim())?.groupValues?.get(1)
            if (assetPath != null && workspace.resolveAsset(assetPath) == null) {
                Toast.makeText(this, R.string.asset_clipboard_unavailable, Toast.LENGTH_LONG).show()
                return
            }
        }

        val sizeBeforePaste = document.size
        val pasted = document.pasteAt(target, source)
        val insertedLineCount = document.size - sizeBeforePaste
        activeLine = activeLine?.let { active ->
            if (insertedLineCount > 0 && active >= pasted.first) {
                active + insertedLineCount
            } else active
        }
        lastActiveLine = lastActiveLine.coerceAtMost(document.size - 1)
        if (transfer != null) clearAssetTransfer()
        recordHistory(MarkdownHistoryKind.Structural, activeLine, 0)
        refreshRows()
        scheduleSave()
        Toast.makeText(this, R.string.pasted, Toast.LENGTH_SHORT).show()
    }

    private fun pasteAtBoundary(boundaryIndex: Int) {
        val transfer = pendingAssetTransfer ?: run {
            noteAdapter.setAssetPastePending(false)
            return
        }
        val boundary = boundaryIndex.coerceIn(0, document.size)
        if (workspace.resolveAsset(transfer.assetPath) == null) {
            clearAssetTransfer()
            Toast.makeText(this, R.string.asset_clipboard_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        val pasted = document.pasteAtInsertion(boundary, transfer.source)
        val insertedLineCount = pasted.count()
        activeLine = activeLine?.let { active ->
            if (active >= pasted.first) active + insertedLineCount else active
        }
        lastActiveLine = lastActiveLine.coerceAtMost(document.size - 1)
        clearAssetTransfer()
        recordHistory(MarkdownHistoryKind.Structural, activeLine, 0)
        refreshRows()
        scheduleSave()
        Toast.makeText(this, R.string.pasted, Toast.LENGTH_SHORT).show()
    }

    private fun clearAssetTransfer() {
        pendingAssetTransfer = null
        if (::noteAdapter.isInitialized) noteAdapter.setAssetPastePending(false)
    }

    private fun showPasteAtBoundary(boundaryIndex: Int) {
        if (pendingAssetTransfer == null) return
        MaterialAlertDialogBuilder(this)
            .setItems(arrayOf(getString(R.string.paste))) { _, _ ->
                pasteAtBoundary(boundaryIndex)
            }
            .show()
    }

    private fun clipboardText(): String? {
        val clipboard = getSystemService<ClipboardManager>() ?: return null
        if (!clipboard.hasPrimaryClip()) return null
        return clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
    }

    private fun confirmDeleteAsset(lineIndex: Int, file: File) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.remove_inserted_file)
            .setMessage(getString(R.string.remove_inserted_file_confirmation, file.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> deleteAsset(lineIndex, file) }
            .show()
    }

    private fun deleteAsset(lineIndex: Int, file: File) {
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
        recordHistory(MarkdownHistoryKind.Structural, activeLine, 0)
        val remainingMarkdown = document.markdown()
        val retainedClipboardReference = listOfNotNull(
            pendingAssetTransfer?.source,
            clipboardText(),
            history.retainedMarkdown()
        ).joinToString("\n")
        io.execute {
            synchronized(workspaceLock) {
                runCatching {
                    workspace.deleteAssetIfUnreferenced(
                        file,
                        "$remainingMarkdown\n$retainedClipboardReference"
                    )
                }
            }
        }
        refreshRows()
        scheduleSave()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (mode == EditorMode.Edit && activeLine != null && event.isCtrlPressed) {
            when {
                keyCode == KeyEvent.KEYCODE_Z && event.isShiftPressed -> redo()
                keyCode == KeyEvent.KEYCODE_Z -> undo()
                keyCode == KeyEvent.KEYCODE_Y -> redo()
                else -> return super.onKeyDown(keyCode, event)
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
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

    private data class AssetTransfer(
        val source: String,
        val assetPath: String
    )

    companion object {
        private const val DISABLED_ALPHA = 0.38f
        private const val SAVE_DELAY_MS = 350L
        private const val EXTRA_FOLDER_NAMES = "folder_names"
        private const val EXTRA_NOTE_NAME = "note_name"
        private val ASSET_EMBED = Regex("""!\[\[asset:(assets/[^|\]]+)(?:\|[^\]]*)?]]""")

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
