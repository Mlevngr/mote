package com.mlevngr.inknote

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.mlevngr.inknote.assets.NoteWorkspace
import com.mlevngr.inknote.library.NoteLibrary
import com.mlevngr.inknote.library.NoteLibrary.FolderLocation
import com.mlevngr.inknote.markdown.MarkdownDocument
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
    private lateinit var titleInput: ImeBackTextInputEditText
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
            onPreviewDoubleTap = ::enterEditModeAt,
            onLineChanged = ::updateLine,
            onSplitLine = ::splitLine,
            onMultilineInput = ::replaceLineFromEditor,
            onMergeWithPrevious = ::mergeWithPrevious,
            onAssetActions = ::showAssetActions,
            onPasteAt = ::showPasteAt,
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
        findViewById<AppCompatImageButton>(R.id.insert_asset).setOnClickListener {
            openAsset.launch(arrayOf("*/*"))
        }
        updateModeButton()
        updateTitleInteraction()
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

    private fun appendLineAtEnd() {
        val lastLine = document.size - 1
        val target = if (document[lastLine].isBlank()) {
            lastLine
        } else {
            document.insertAfter(lastLine, "")
        }
        enterEditModeAt(target)
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

    private fun showAssetActions(lineIndex: Int, file: File, label: String) {
        val actions = arrayOf(
            getString(R.string.move_inserted_file),
            getString(R.string.copy_inserted_file),
            getString(R.string.remove_inserted_file)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(label.ifBlank { file.name })
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> stageAssetTransfer(lineIndex, file, label, move = true)
                    1 -> stageAssetTransfer(lineIndex, file, label, move = false)
                    2 -> confirmDeleteAsset(lineIndex, file)
                }
            }
            .show()
    }

    private fun stageAssetTransfer(lineIndex: Int, file: File, label: String, move: Boolean) {
        if (lineIndex !in 0 until document.size) return
        val source = document[lineIndex]
        val assetPath = runCatching {
            file.canonicalFile.relativeTo(workspace.root.canonicalFile).invariantSeparatorsPath
        }.getOrElse { file.name }
        pendingAssetTransfer = AssetTransfer(source, lineIndex, assetPath, move)
        val clipboard = getSystemService<ClipboardManager>()
        if (move) {
            if (clipboardText() == source) clipboard?.clearPrimaryClip()
        } else {
            clipboard?.setPrimaryClip(
                ClipData.newPlainText(label.ifBlank { file.name }, source)
            )
        }
        Toast.makeText(
            this,
            if (move) R.string.asset_ready_to_move else R.string.asset_copied,
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
            pending.move || clipboardText == null || clipboardText == pending.source
        }
        if (stagedTransfer != null && transfer == null) pendingAssetTransfer = null
        val target = targetLine?.coerceIn(0, document.size - 1)
        if (transfer?.move == true) {
            val currentSource = locateTransferSource(transfer)
            if (currentSource < 0) {
                pendingAssetTransfer = null
                Toast.makeText(this, R.string.asset_move_source_unavailable, Toast.LENGTH_LONG)
                    .show()
                return
            }
            val replacesBlank = target != null &&
                target != currentSource &&
                document[target].isBlank()
            val destination = document.moveLineToPasteTarget(currentSource, target)
            activeLine = remapLineAfterMove(
                activeLine,
                currentSource,
                destination,
                replacesBlank
            )
            lastActiveLine = remapLineAfterMove(
                lastActiveLine,
                currentSource,
                destination,
                replacesBlank
            ) ?: 0
            pendingAssetTransfer = null
            refreshRows()
            scheduleSave()
            Toast.makeText(this, R.string.asset_moved, Toast.LENGTH_SHORT).show()
            return
        }

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
        refreshRows()
        scheduleSave()
        Toast.makeText(this, R.string.pasted, Toast.LENGTH_SHORT).show()
    }

    private fun remapLineAfterMove(
        lineIndex: Int?,
        source: Int,
        destination: Int,
        replacedBlank: Boolean
    ): Int? {
        lineIndex ?: return null
        if (source == destination) return lineIndex
        if (lineIndex == source) return destination

        var mapped = if (lineIndex > source) lineIndex - 1 else lineIndex
        if (!replacedBlank && mapped >= destination) mapped++
        return mapped.coerceIn(0, document.size - 1)
    }

    private fun locateTransferSource(transfer: AssetTransfer): Int {
        if (transfer.originalLine in 0 until document.size &&
            transfer.assetPath in document[transfer.originalLine]
        ) return transfer.originalLine
        val pathMatches = document.snapshot().mapIndexedNotNull { index, line ->
            index.takeIf { transfer.assetPath in line }
        }
        return pathMatches.singleOrNull() ?: document.snapshot().indexOf(transfer.source)
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
        val remainingMarkdown = document.markdown()
        val retainedClipboardReference = pendingAssetTransfer?.source.orEmpty()
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
        val originalLine: Int,
        val assetPath: String,
        val move: Boolean
    )

    companion object {
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
