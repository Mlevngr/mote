package com.mlevngr.inknote

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.mlevngr.inknote.assets.ImportedAsset
import com.mlevngr.inknote.assets.NoteWorkspace
import com.mlevngr.inknote.markdown.MarkdownDocument
import com.mlevngr.inknote.ui.HybridNoteAdapter
import com.mlevngr.inknote.ui.HybridRowFactory
import com.mlevngr.inknote.ui.PreviewRowFactory
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : AppCompatActivity() {
    private lateinit var workspace: NoteWorkspace
    private lateinit var document: MarkdownDocument
    private lateinit var noteAdapter: HybridNoteAdapter
    private lateinit var rowFactory: HybridRowFactory
    private lateinit var recyclerView: RecyclerView
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val renderRevision = AtomicInteger()
    private var activeBlock: Int? = null
    private var saveTask: Runnable? = null

    private val openImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importAsset(it, ImportedAsset.Kind.Image) }
    }

    private val openPdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importAsset(it, ImportedAsset.Kind.Pdf) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        workspace = NoteWorkspace(this)
        document = MarkdownDocument.parse(workspace.load(DEFAULT_NOTE))
        rowFactory = HybridRowFactory(PreviewRowFactory(workspace))
        noteAdapter = HybridNoteAdapter(
            context = this,
            onActivate = ::activateBlock,
            onBlockChanged = ::updateBlock
        )

        findViewById<MaterialToolbar>(R.id.toolbar).title = getString(R.string.app_name)
        recyclerView = findViewById<RecyclerView>(R.id.note_content).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = noteAdapter
            itemAnimator = null
        }
        findViewById<MaterialButton>(R.id.finish_editing).setOnClickListener {
            finishEditing()
        }
        findViewById<MaterialButton>(R.id.add_image).setOnClickListener {
            openImage.launch(arrayOf("image/*"))
        }
        findViewById<MaterialButton>(R.id.add_pdf).setOnClickListener {
            openPdf.launch(arrayOf("application/pdf"))
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (activeBlock != null) {
                    finishEditing()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        refreshRows()
    }

    private fun activateBlock(index: Int) {
        if (activeBlock == index) return
        activeBlock = index
        refreshRows(requestFocus = true)
    }

    private fun updateBlock(index: Int, source: String) {
        if (index !in 0 until document.size) return
        document.update(index, source)
        scheduleSave()
    }

    private fun finishEditing() {
        if (activeBlock == null) return
        activeBlock = null
        getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(
            recyclerView.windowToken,
            0
        )
        refreshRows()
        scheduleSave()
    }

    private fun refreshRows(requestFocus: Boolean = false) {
        val revision = renderRevision.incrementAndGet()
        val blocks = document.snapshot()
        val active = activeBlock
        io.execute {
            val rows = rowFactory.create(blocks, active)
            main.post {
                if (!isDestroyed && revision == renderRevision.get()) {
                    noteAdapter.submit(rows, active.takeIf { requestFocus })
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

    private fun importAsset(uri: Uri, kind: ImportedAsset.Kind) {
        io.execute {
            val result = runCatching { workspace.import(contentResolver, uri, kind) }
            main.post {
                result.onSuccess { asset ->
                    document.insertAfter(activeBlock, asset.markdown())
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
        val markdown = document.markdown()
        io.execute { workspace.save(markdown) }
    }

    override fun onDestroy() {
        saveTask?.let(main::removeCallbacks)
        noteAdapter.close()
        io.shutdown()
        super.onDestroy()
    }

    private companion object {
        const val SAVE_DELAY_MS = 350L
        val DEFAULT_NOTE = """
            # InkNote

            点击任意段落进行 Markdown 编辑；点击“完成”后，该段落恢复实时预览。

            - 只有正在编辑的段落显示 Markdown 源码
            - 其他文字、图片和 PDF 始终保持渲染状态
            - 点击“图片”或“PDF”即可把文件插入当前段落之后
        """.trimIndent()
    }
}
