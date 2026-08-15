package com.mlevngr.inknote

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.mlevngr.inknote.assets.ImportedAsset
import com.mlevngr.inknote.assets.NoteWorkspace
import com.mlevngr.inknote.ui.PreviewAdapter
import com.mlevngr.inknote.ui.PreviewRowFactory
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : AppCompatActivity() {
    private lateinit var editor: TextInputEditText
    private lateinit var workspace: NoteWorkspace
    private lateinit var previewAdapter: PreviewAdapter
    private lateinit var previewFactory: PreviewRowFactory
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val previewRevision = AtomicInteger()
    private var saveTask: Runnable? = null
    private var previewTask: Runnable? = null

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
        previewFactory = PreviewRowFactory(workspace)
        previewAdapter = PreviewAdapter(this)
        editor = findViewById(R.id.markdown_editor)

        findViewById<MaterialToolbar>(R.id.toolbar).title = getString(R.string.app_name)
        findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.preview).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = previewAdapter
            itemAnimator = null
        }
        findViewById<MaterialButton>(R.id.add_image).setOnClickListener {
            openImage.launch(arrayOf("image/*"))
        }
        findViewById<MaterialButton>(R.id.add_pdf).setOnClickListener {
            openPdf.launch(arrayOf("application/pdf"))
        }

        val initial = workspace.load(DEFAULT_NOTE)
        editor.setText(initial)
        editor.setSelection(initial.length)
        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                scheduleSaveAndPreview(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        renderPreview(initial)
    }

    private fun scheduleSaveAndPreview(markdown: String) {
        saveTask?.let(main::removeCallbacks)
        previewTask?.let(main::removeCallbacks)
        saveTask = Runnable { io.execute { workspace.save(markdown) } }.also {
            main.postDelayed(it, SAVE_DELAY_MS)
        }
        previewTask = Runnable { renderPreview(markdown) }.also {
            main.postDelayed(it, PREVIEW_DELAY_MS)
        }
    }

    private fun renderPreview(markdown: String) {
        val revision = previewRevision.incrementAndGet()
        io.execute {
            val rows = previewFactory.create(markdown)
            main.post {
                if (revision == previewRevision.get()) previewAdapter.submit(rows)
            }
        }
    }

    private fun importAsset(uri: Uri, kind: ImportedAsset.Kind) {
        io.execute {
            val result = runCatching { workspace.import(contentResolver, uri, kind) }
            main.post {
                result.onSuccess(::insertAsset).onFailure {
                    Toast.makeText(
                        this,
                        getString(R.string.import_failed, it.message ?: "unknown error"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun insertAsset(asset: ImportedAsset) {
        val text = editor.text ?: return
        val insertion = buildString {
            if (editor.selectionStart > 0 && text[editor.selectionStart - 1] != '\n') append("\n\n")
            append(asset.markdown())
            append("\n\n")
        }
        text.insert(editor.selectionStart.coerceAtLeast(0), insertion)
    }

    override fun onStop() {
        super.onStop()
        val markdown = editor.text?.toString().orEmpty()
        io.execute { workspace.save(markdown) }
    }

    override fun onDestroy() {
        saveTask?.let(main::removeCallbacks)
        previewTask?.let(main::removeCallbacks)
        previewAdapter.close()
        io.shutdown()
        super.onDestroy()
    }

    private companion object {
        const val PREVIEW_DELAY_MS = 140L
        const val SAVE_DELAY_MS = 350L
        val DEFAULT_NOTE = """
            # InkNote

            在上方使用 Markdown 编辑，下面会实时预览。

            - 支持 **粗体**、*斜体*、标题、引用和列表
            - 点击“图片”或“PDF”即可导入，内容会直接出现在预览中
            - 笔记和附件都只保存在本机
        """.trimIndent()
    }
}
