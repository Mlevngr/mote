package com.mlevngr.inknote

import android.net.Uri
import android.content.Intent
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
import com.mlevngr.inknote.assets.NoteWorkspace
import com.mlevngr.inknote.library.NoteLibrary
import com.mlevngr.inknote.markdown.MarkdownDocument
import com.mlevngr.inknote.ui.HybridNoteAdapter
import com.mlevngr.inknote.ui.HybridRowFactory
import com.mlevngr.inknote.ui.PreviewRowFactory
import com.mlevngr.inknote.ui.SystemBarInsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class EditorActivity : AppCompatActivity() {
    private lateinit var workspace: NoteWorkspace
    private lateinit var document: MarkdownDocument
    private lateinit var noteAdapter: HybridNoteAdapter
    private lateinit var rowFactory: HybridRowFactory
    private lateinit var recyclerView: RecyclerView
    private lateinit var modeButton: AppCompatImageButton
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val renderRevision = AtomicInteger()
    private var mode = EditorMode.Read
    private var activeLine: Int? = null
    private var lastActiveLine = 0
    private var saveTask: Runnable? = null

    private val openAsset = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::importAsset)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)
        SystemBarInsets.install(findViewById(R.id.app_root))

        val notePath = intent.getStringExtra(EXTRA_NOTE_PATH)
            ?.takeIf(String::isNotBlank)
            ?: run {
                finish()
                return
            }
        val initialState = runCatching {
            NoteWorkspace(this, notePath).let { it to MarkdownDocument.parse(it.load(DEFAULT_NOTE)) }
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
            onMergeWithPrevious = ::mergeWithPrevious
        )

        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            title = NoteLibrary(this@EditorActivity).displayNameForNote(notePath)
            setNavigationIcon(R.drawable.ic_arrow_back_24)
            navigationContentDescription = getString(R.string.back_to_library)
            setNavigationOnClickListener { finish() }
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
        refreshRows(requestFocus = true)
    }

    private fun enterReadMode() {
        mode = EditorMode.Read
        activeLine?.let { lastActiveLine = it }
        activeLine = null
        getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(recyclerView.windowToken, 0)
        recyclerView.clearFocus()
        updateModeButton()
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
                }
            }
        }
    }

    private fun scheduleSave() {
        saveTask?.let(main::removeCallbacks)
        val markdown = document.markdown()
        saveTask = Runnable { io.execute { workspace.save(markdown) } }.also {
            main.postDelayed(it, SAVE_DELAY_MS)
        }
    }

    private fun importAsset(uri: Uri) {
        io.execute {
            val result = runCatching { workspace.import(contentResolver, uri) }
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

    override fun onStop() {
        super.onStop()
        if (!::document.isInitialized || !::workspace.isInitialized) return
        val markdown = document.markdown()
        io.execute { workspace.save(markdown) }
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
        private const val EXTRA_NOTE_PATH = "note_path"

        fun intent(context: android.content.Context, notePath: String): Intent =
            Intent(context, EditorActivity::class.java).putExtra(EXTRA_NOTE_PATH, notePath)

        val DEFAULT_NOTE = """
            # InkNote

            阅读模式没有光标，所有内容都是渲染结果。

            点击右上角编辑图标进入编辑模式；只有光标所在行显示 Markdown 源码。

            使用链接形状的插入按钮选择图片、PDF 或其他文件，文件会复制到笔记内部。
        """.trimIndent()
    }
}
