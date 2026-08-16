package com.mlevngr.inknote.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.getSystemService
import androidx.core.view.setPadding
import androidx.recyclerview.widget.RecyclerView
import com.mlevngr.inknote.R
import com.mlevngr.inknote.appearance.ThemeColors
import com.mlevngr.inknote.markdown.MarkdownAutoPairing
import com.mlevngr.inknote.markdown.MarkdownEditResult
import com.mlevngr.inknote.markdown.MarkdownHistoryKind
import com.mlevngr.inknote.pdf.PdfDocumentSource
import com.mlevngr.inknote.ui.AssetPreviewVisibility.AssetInstanceKey
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import java.io.Closeable
import java.io.File
import java.util.concurrent.Executors

class HybridNoteAdapter(
    private val context: Context,
    private val onActivate: (Int) -> Unit,
    private val onPreviewDoubleTap: (Int) -> Unit,
    private val onLineChanged: (Int, String, MarkdownHistoryKind, Int, Int) -> Unit,
    private val onSplitLine: (Int, Int) -> Unit,
    private val onMultilineInput: (Int, String, Int) -> Unit,
    private val onMergeWithPrevious: (Int) -> Boolean,
    private val onAssetActions: (Int, File, String) -> Unit,
    private val onAddPdfPageNote: (Int, Int) -> Unit,
    private val onPasteAt: (Int?) -> Unit,
    private val onPasteAtBoundary: (Int) -> Unit,
    private val onAppendAtEnd: () -> Unit,
    private val onBackFromIme: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), Closeable {

    private val markwon = Markwon.builder(context)
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(TablePlugin.create(context))
        .usePlugin(TaskListPlugin.create(context))
        .build()
    private val worker = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private val pdfSources = mutableMapOf<String, PdfDocumentSource>()
    private val bitmapCache = object : LruCache<String, Bitmap>(48 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private var allRows: List<HybridRow> = emptyList()
    private var rows: List<HybridRow> = emptyList()
    private val collapsedAssets = mutableSetOf<AssetInstanceKey>()
    private val collapsedPdfPages = mutableSetOf<PdfPreviewVisibility.PageKey>()
    private var assetPastePending = false
    private var focusLine: Int? = null
    private var focusCursor: Int? = null
    private var focusSelectionStart: Int? = null
    private var editing = false
    private var activeEditor: LineEditText? = null
    private var activeEditorLine: Int? = null
    private var pendingEdit: PendingEdit? = null
    private val horizontalPadding = dp(18)
    private val targetWidth get() = context.resources.displayMetrics.widthPixels - dp(36)

    fun submit(
        newRows: List<HybridRow>,
        editing: Boolean,
        focusLine: Int? = null,
        focusCursor: Int? = null,
        focusSelectionStart: Int? = null
    ) {
        migratePdfCollapseKeys(allRows, newRows)
        allRows = newRows
        val availableAssets = newRows.mapNotNull(AssetPreviewVisibility::assetKey).toSet()
        collapsedAssets.retainAll(availableAssets)
        val availablePages = newRows.mapNotNull { row ->
            val preview = (row as? HybridRow.Rendered)?.preview as? PreviewRow.PdfPage
                ?: return@mapNotNull null
            val instanceKey = preview.instanceKey ?: return@mapNotNull null
            PdfPreviewVisibility.PageKey(instanceKey, preview.pageIndex)
        }.toSet()
        collapsedPdfPages.retainAll(availablePages)
        this.editing = editing
        this.focusLine = focusLine
        this.focusCursor = focusCursor
        this.focusSelectionStart = focusSelectionStart
        if (!editing) pendingEdit = null
        rebuildVisibleRows()
        notifyDataSetChanged()
    }

    private fun migratePdfCollapseKeys(
        oldRows: List<HybridRow>,
        newRows: List<HybridRow>
    ) {
        fun identities(rows: List<HybridRow>): Map<Pair<Int, String>, String> =
            rows.mapNotNull { row ->
                val page = (row as? HybridRow.Rendered)?.preview as? PreviewRow.PdfPage
                    ?: return@mapNotNull null
                val identity = page.instanceKey
                    ?: "legacy:${row.lineIndex}:${page.file.canonicalPath}"
                (row.lineIndex to page.file.canonicalPath) to identity
            }.toMap()

        val oldIdentities = identities(oldRows)
        val newIdentities = identities(newRows)
        oldIdentities.forEach { (location, oldIdentity) ->
            val newIdentity = newIdentities[location] ?: return@forEach
            if (oldIdentity == newIdentity) return@forEach
            if (collapsedAssets.remove(AssetInstanceKey(oldIdentity))) {
                collapsedAssets += AssetInstanceKey(newIdentity)
            }
            val migratedPages = collapsedPdfPages.filter { it.instanceKey == oldIdentity }
            collapsedPdfPages.removeAll(migratedPages.toSet())
            collapsedPdfPages += migratedPages.map { it.copy(instanceKey = newIdentity) }
        }
    }

    fun setAssetPastePending(pending: Boolean) {
        if (assetPastePending == pending) return
        assetPastePending = pending
        notifyDataSetChanged()
    }

    fun positionOfLine(lineIndex: Int): Int = rows.indexOfFirst { it.lineIndex == lineIndex }

    fun editActiveLine(
        lineIndex: Int,
        transform: (source: String, selectionStart: Int, selectionEnd: Int) -> MarkdownEditResult
    ) {
        val editor = activeEditor?.takeIf {
            it.isAttachedToWindow && activeEditorLine == lineIndex
        }
        if (editor == null) {
            pendingEdit = PendingEdit(lineIndex, transform)
            return
        }
        applyEdit(editor, transform)
    }

    fun activeEditState(lineIndex: Int): MarkdownEditResult? {
        val editor = activeEditor?.takeIf {
            it.isAttachedToWindow && activeEditorLine == lineIndex
        } ?: return null
        val source = editor.text?.toString().orEmpty()
        return MarkdownEditResult(
            source = source,
            selectionStart = editor.selectionStart.coerceIn(0, source.length),
            selectionEnd = editor.selectionEnd.coerceIn(0, source.length)
        )
    }

    private fun applyEdit(
        editor: LineEditText,
        transform: (source: String, selectionStart: Int, selectionEnd: Int) -> MarkdownEditResult
    ) {
        val source = editor.text?.toString().orEmpty()
        val selectionStart = editor.selectionStart.coerceAtLeast(0)
        val selectionEnd = editor.selectionEnd.coerceAtLeast(0)
        val result = transform(source, selectionStart, selectionEnd)
        if (result.source != source) {
            editor.nextHistoryKind = MarkdownHistoryKind.Structural
            editor.nextHistorySelection = result.selectionStart to result.selectionEnd
            editor.editableText.replace(0, editor.editableText.length, result.source)
        }
        editor.setSelection(
            result.selectionStart.coerceIn(0, editor.length()),
            result.selectionEnd.coerceIn(0, editor.length())
        )
        editor.requestFocus()
        context.getSystemService<InputMethodManager>()
            ?.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun getItemCount(): Int = rows.size + 1

    override fun getItemViewType(position: Int): Int {
        if (position == rows.size) return TYPE_END_ZONE
        return when (val row = rows[position]) {
            is HybridRow.Editor -> TYPE_EDITOR
            is HybridRow.Rendered -> when (row.preview) {
                is PreviewRow.Markdown -> TYPE_MARKDOWN
                is PreviewRow.Image -> TYPE_IMAGE
                is PreviewRow.PdfPage -> TYPE_PDF
                is PreviewRow.Attachment -> TYPE_ATTACHMENT
                is PreviewRow.Error -> TYPE_ERROR
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (viewType) {
            TYPE_EDITOR -> EditorHolder(LineEditText(context).apply {
                onImeBack = onBackFromIme
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(horizontalPadding, dp(9), horizontalPadding, dp(9))
                setBackgroundResource(R.drawable.editor_background)
                typeface = Typeface.MONOSPACE
                textSize = 16f
                minHeight = dp(48)
                gravity = Gravity.TOP or Gravity.START
                isSingleLine = false
                maxLines = Int.MAX_VALUE
                setHorizontallyScrolling(false)
                inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                imeOptions = EditorInfo.IME_ACTION_NEXT
                isLongClickable = true
            })
            TYPE_MARKDOWN, TYPE_ATTACHMENT, TYPE_ERROR -> TextHolder(TextView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(horizontalPadding, dp(8), horizontalPadding, dp(8))
                minHeight = dp(40)
                gravity = Gravity.CENTER_VERTICAL
                textSize = 17f
            })
            TYPE_END_ZONE -> TextHolder(TextView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(horizontalPadding, dp(24), horizontalPadding, dp(40))
                minHeight = dp(112)
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                textSize = 13f
                setTextColor(ThemeColors.resolve(context, R.attr.inkNoteTextSecondary))
                text = context.getString(R.string.document_end_hint)
                contentDescription = context.getString(R.string.document_end_hint)
            })
            else -> AssetHolder(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(horizontalPadding, dp(8), horizontalPadding, dp(12))
            })
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (position == rows.size) {
            bindEndZone(holder as TextHolder)
            return
        }
        bindContent(holder, rows[position])
    }

    private fun bindContent(holder: RecyclerView.ViewHolder, row: HybridRow) {
        when (row) {
            is HybridRow.Editor -> {
                bindEditor(holder as EditorHolder, row)
                holder.editor.setOnLongClickListener(null)
            }
            is HybridRow.Rendered -> {
                val activationListener = activationListener(row.lineIndex)
                if (editing) {
                    holder.itemView.setOnTouchListener(null)
                    holder.itemView.setOnClickListener(activationListener)
                } else {
                    holder.itemView.setOnClickListener(null)
                    holder.itemView.setOnTouchListener(
                        doubleTapListener { onPreviewDoubleTap(row.lineIndex) }
                    )
                }
                when (val preview = row.preview) {
                    is PreviewRow.Markdown -> {
                        holder as TextHolder
                        val blank = preview.source == "\u00a0"
                        holder.text.setTextIsSelectable(!editing && !blank)
                        val pasteListener = pasteAtBoundaryListener(row.lineIndex) ?: if (blank) {
                            View.OnLongClickListener { onPasteAt(row.lineIndex); true }
                        } else null
                        holder.text.setOnLongClickListener(pasteListener)
                        holder.itemView.setOnLongClickListener(pasteListener)
                        holder.text.setTextColor(ThemeColors.resolve(context, R.attr.inkNoteTextPrimary))
                        markwon.setMarkdown(holder.text, preview.source)
                    }
                    is PreviewRow.Attachment -> {
                        holder as TextHolder
                        holder.text.setTextIsSelectable(false)
                        bindAssetActions(
                            holder.itemView,
                            holder.text,
                            null,
                            null,
                            row.lineIndex,
                            preview.file,
                            preview.label
                        )
                        holder.text.setTextColor(ThemeColors.resolve(context, androidx.appcompat.R.attr.colorPrimary))
                        holder.text.text = "📎  ${preview.label}  •  ${formatSize(preview.file.length())}"
                    }
                    is PreviewRow.Error -> {
                        holder as TextHolder
                        holder.text.setTextIsSelectable(false)
                        val pasteListener = pasteAtBoundaryListener(row.lineIndex)
                        holder.text.setOnLongClickListener(pasteListener)
                        holder.itemView.setOnLongClickListener(pasteListener)
                        holder.text.setTextColor(ThemeColors.resolve(context, R.attr.inkNoteErrorText))
                        holder.text.text = preview.message
                    }
                    is PreviewRow.Image -> {
                        holder as AssetHolder
                        bindAssetActions(
                            holder.itemView,
                            holder.caption,
                            holder.menu,
                            holder.image,
                            row.lineIndex,
                            preview.file,
                            preview.label.ifBlank { preview.file.name }
                        )
                        bindImage(holder, row.lineIndex, preview)
                    }
                    is PreviewRow.PdfPage -> {
                        holder as AssetHolder
                        bindAssetActions(
                            holder.itemView,
                            holder.caption,
                            holder.menu,
                            holder.image,
                            row.lineIndex,
                            preview.file,
                            preview.label.ifBlank { preview.file.name }
                        )
                        bindPdf(holder, row.lineIndex, preview)
                    }
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindEndZone(holder: TextHolder) {
        val hint = context.getString(R.string.document_end_hint)
        holder.text.text = if (editing) "" else hint
        holder.text.contentDescription = if (editing) null else hint
        holder.text.minHeight = dp(if (editing) 64 else 112)
        holder.text.setOnClickListener(null)
        holder.text.setOnTouchListener(doubleTapListener(onAppendAtEnd))
        holder.text.setOnLongClickListener {
            onPasteAt(null)
            true
        }
    }

    private fun activationListener(lineIndex: Int): View.OnClickListener {
        return View.OnClickListener { onActivate(lineIndex) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun doubleTapListener(action: () -> Unit): View.OnTouchListener {
        val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            override fun onDoubleTap(event: MotionEvent): Boolean {
                action()
                return true
            }
        })
        return View.OnTouchListener { _, event ->
            detector.onTouchEvent(event)
            false
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        holder.itemView.setOnClickListener(null)
        holder.itemView.setOnLongClickListener(null)
        holder.itemView.setOnTouchListener(null)
        if (holder is EditorHolder) {
            if (activeEditor === holder.editor) {
                activeEditor = null
                activeEditorLine = null
            }
            holder.detach()
        }
        if (holder is AssetHolder) {
            holder.caption.setOnClickListener(null)
            holder.caption.setOnLongClickListener(null)
            holder.menu.setOnClickListener(null)
            holder.documentToggle.setOnClickListener(null)
            holder.pageNote.setOnClickListener(null)
            holder.image.setOnLongClickListener(null)
            holder.image.tag = null
            holder.image.setImageDrawable(null)
        }
    }

    private fun bindEditor(holder: EditorHolder, row: HybridRow.Editor) {
        activeEditor = holder.editor
        activeEditorLine = row.lineIndex
        holder.bind(
            row.lineIndex,
            row.source,
            onLineChanged,
            onSplitLine,
            onMultilineInput,
            onMergeWithPrevious
        )
        if (focusLine == row.lineIndex) {
            focusLine = null
            val cursor = focusCursor
            val selectionStart = focusSelectionStart
            focusCursor = null
            focusSelectionStart = null
            holder.editor.post {
                holder.editor.requestFocus()
                val end = (cursor ?: holder.editor.text?.length ?: 0)
                    .coerceIn(0, holder.editor.text?.length ?: 0)
                val start = (selectionStart ?: end).coerceIn(0, end)
                holder.editor.setSelection(start, end)
                context.getSystemService<InputMethodManager>()
                    ?.showSoftInput(holder.editor, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        pendingEdit?.takeIf { it.lineIndex == row.lineIndex }?.let { pending ->
            pendingEdit = null
            holder.editor.post {
                if (activeEditor === holder.editor && activeEditorLine == row.lineIndex) {
                    applyEdit(holder.editor, pending.transform)
                }
            }
        }
    }

    private fun bindImage(holder: AssetHolder, lineIndex: Int, row: PreviewRow.Image) {
        holder.documentToggle.visibility = View.GONE
        holder.pageNote.visibility = View.GONE
        val collapsed = isCollapsed(lineIndex, row.file)
        bindAssetHeader(
            holder,
            lineIndex,
            row.file,
            row.label.ifBlank { row.file.name },
            collapsed
        )
        holder.image.visibility = if (collapsed) View.GONE else View.VISIBLE
        if (collapsed) {
            holder.image.tag = null
            holder.image.setImageDrawable(null)
            return
        }
        val key = "image:${row.file.path}:${row.file.lastModified()}:$targetWidth"
        loadBitmap(holder, key) { decodeImage(row.file, targetWidth) }
    }

    private fun bindPdf(holder: AssetHolder, lineIndex: Int, row: PreviewRow.PdfPage) {
        val instanceKey = row.instanceKey
            ?: "legacy:$lineIndex:${row.file.canonicalPath}"
        val documentKey = AssetInstanceKey(instanceKey)
        val documentCollapsed = documentKey in collapsedAssets
        val pageKey = PdfPreviewVisibility.PageKey(instanceKey, row.pageIndex)
        val pageCollapsed = !PdfPreviewVisibility.isPageExpanded(pageKey, collapsedPdfPages)
        val pageLabel = if (documentCollapsed) {
            "${row.label}  •  ${context.getString(R.string.pdf_page_count, row.pageCount)}"
        } else {
            "${row.label}  •  ${row.pageIndex + 1}/${row.pageCount}"
        }
        holder.caption.text = "${if (documentCollapsed || pageCollapsed) '▶' else '▼'}  $pageLabel"
        holder.caption.contentDescription = context.getString(
            if (documentCollapsed || pageCollapsed) R.string.expand_pdf_page
            else R.string.collapse_pdf_page,
            row.pageIndex + 1
        )
        holder.caption.setOnClickListener {
            if (documentCollapsed) togglePdfDocument(documentKey)
            else togglePdfPage(pageKey)
        }
        holder.documentToggle.visibility = if (row.pageIndex == 0) View.VISIBLE else View.GONE
        holder.documentToggle.setImageResource(
            if (documentCollapsed) R.drawable.ic_unfold_more_24 else R.drawable.ic_unfold_less_24
        )
        holder.documentToggle.contentDescription = context.getString(
            if (documentCollapsed) R.string.expand_entire_pdf else R.string.collapse_entire_pdf
        )
        holder.documentToggle.setOnClickListener { togglePdfDocument(documentKey) }
        holder.pageNote.visibility = if (editing && !documentCollapsed) View.VISIBLE else View.GONE
        holder.pageNote.text = context.getString(R.string.add_pdf_page_note)
        holder.pageNote.contentDescription = context.getString(
            R.string.add_pdf_page_note_for_page,
            row.pageIndex + 1
        )
        holder.pageNote.setOnClickListener { onAddPdfPageNote(lineIndex, row.pageIndex) }

        val previewVisible = !documentCollapsed && !pageCollapsed
        holder.image.visibility = if (previewVisible) View.VISIBLE else View.GONE
        if (!previewVisible) {
            holder.image.tag = null
            holder.image.setImageDrawable(null)
            return
        }
        val key = "pdf:${row.file.path}:${row.file.lastModified()}:${row.pageIndex}:$targetWidth"
        loadBitmap(holder, key) {
            val source = synchronized(pdfSources) {
                pdfSources.getOrPut(row.file.path) { PdfDocumentSource(row.file) }
            }
            source.render(row.pageIndex, targetWidth)
        }
    }

    private fun bindAssetHeader(
        holder: AssetHolder,
        lineIndex: Int,
        file: File,
        label: String,
        collapsed: Boolean
    ) {
        holder.caption.text = "${if (collapsed) '▶' else '▼'}  $label"
        holder.caption.contentDescription = context.getString(
            if (collapsed) R.string.expand_preview else R.string.collapse_preview,
            label
        )
        holder.caption.setOnClickListener { toggleAsset(lineIndex, file) }
    }

    private fun bindAssetActions(
        itemView: View,
        actionView: View,
        menuView: View?,
        contentView: View?,
        lineIndex: Int,
        file: File,
        label: String
    ) {
        val listener = pasteAtBoundaryListener(lineIndex) ?: View.OnLongClickListener {
            onAssetActions(lineIndex, file, label)
            true
        }
        itemView.setOnLongClickListener(listener)
        actionView.setOnLongClickListener(listener)
        contentView?.setOnLongClickListener(listener)
        menuView?.setOnClickListener { onAssetActions(lineIndex, file, label) }
    }

    private fun pasteAtBoundaryListener(lineIndex: Int): View.OnLongClickListener? =
        if (assetPastePending) {
            View.OnLongClickListener {
                onPasteAtBoundary(lineIndex)
                true
            }
        } else null

    private fun assetKey(lineIndex: Int, file: File) =
        AssetInstanceKey(lineIndex, file.canonicalPath)

    private fun isCollapsed(lineIndex: Int, file: File): Boolean =
        assetKey(lineIndex, file) in collapsedAssets

    private fun toggleAsset(lineIndex: Int, file: File) {
        val key = assetKey(lineIndex, file)
        if (!collapsedAssets.add(key)) collapsedAssets.remove(key)
        rebuildVisibleRows()
        notifyDataSetChanged()
    }

    private fun togglePdfDocument(key: AssetInstanceKey) {
        if (!collapsedAssets.add(key)) collapsedAssets.remove(key)
        rebuildVisibleRows()
        notifyDataSetChanged()
    }

    private fun togglePdfPage(key: PdfPreviewVisibility.PageKey) {
        if (!collapsedPdfPages.add(key)) collapsedPdfPages.remove(key)
        notifyDataSetChanged()
    }

    private fun rebuildVisibleRows() {
        rows = AssetPreviewVisibility.visibleRows(allRows, collapsedAssets)
    }

    private fun loadBitmap(holder: AssetHolder, key: String, loader: () -> Bitmap) {
        holder.image.tag = key
        bitmapCache.get(key)?.let {
            holder.image.setImageBitmap(it)
            return
        }
        holder.image.setImageDrawable(null)
        worker.execute {
            val bitmap = runCatching(loader).getOrNull() ?: return@execute
            bitmapCache.put(key, bitmap)
            main.post { if (holder.image.tag == key) holder.image.setImageBitmap(bitmap) }
        }
    }

    private fun decodeImage(file: File, targetWidth: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / sample > targetWidth * 2) sample *= 2
        return requireNotNull(BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
            inSampleSize = sample
        })) { "Unsupported image" }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024f * 1024f))
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024f)
        else -> "$bytes B"
    }

    override fun close() {
        worker.shutdownNow()
        synchronized(pdfSources) {
            pdfSources.values.forEach { runCatching(it::close) }
            pdfSources.clear()
        }
        bitmapCache.evictAll()
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private class EditorHolder(val editor: LineEditText) : RecyclerView.ViewHolder(editor) {
        private var watcher: TextWatcher? = null

        fun bind(
            lineIndex: Int,
            source: String,
            onChanged: (Int, String, MarkdownHistoryKind, Int, Int) -> Unit,
            onSplit: (Int, Int) -> Unit,
            onMultiline: (Int, String, Int) -> Unit,
            onMerge: (Int) -> Boolean
        ) {
            detach()
            var handlingLineBreak = false
            if (editor.text?.toString() != source) editor.setText(source)
            watcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val value = s?.toString().orEmpty()
                    if ('\n' !in value && '\r' !in value) {
                        val inferredKind = when {
                            before == 0 && count > 0 -> MarkdownHistoryKind.Insert
                            before > 0 && count == 0 -> MarkdownHistoryKind.Delete
                            else -> MarkdownHistoryKind.Replace
                        }
                        val fallbackCursor = (start + count).coerceIn(0, value.length)
                        val selection = editor.consumeHistorySelection(fallbackCursor)
                        onChanged(
                            lineIndex,
                            value,
                            editor.consumeHistoryKind(inferredKind),
                            selection.first,
                            selection.second
                        )
                    }
                }

                override fun afterTextChanged(s: Editable?) {
                    val value = s?.toString().orEmpty()
                    if (!handlingLineBreak && ('\n' in value || '\r' in value)) {
                        handlingLineBreak = true
                        onMultiline(lineIndex, value, editor.selectionStart.coerceAtLeast(0))
                    }
                }
            }.also(editor::addTextChangedListener)
            editor.onDeleteAtStart = { onMerge(lineIndex) }
            editor.setOnEditorActionListener { _, actionId, event ->
                val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                    event.action == KeyEvent.ACTION_DOWN
                if (actionId == EditorInfo.IME_ACTION_NEXT || enter) {
                    onSplit(lineIndex, editor.selectionStart.coerceAtLeast(0))
                    true
                } else false
            }
        }

        fun detach() {
            watcher?.let(editor::removeTextChangedListener)
            watcher = null
            editor.setOnEditorActionListener(null)
            editor.onDeleteAtStart = null
        }
    }

    private class LineEditText(context: Context) : ImeBackTextInputEditText(context) {
        var onDeleteAtStart: (() -> Boolean)? = null
        var nextHistoryKind: MarkdownHistoryKind? = null
        var nextHistorySelection: Pair<Int, Int>? = null

        fun consumeHistoryKind(fallback: MarkdownHistoryKind): MarkdownHistoryKind =
            nextHistoryKind.also { nextHistoryKind = null } ?: fallback

        fun consumeHistorySelection(fallback: Int): Pair<Int, Int> =
            nextHistorySelection.also { nextHistorySelection = null } ?: (fallback to fallback)

        private fun applyEdit(result: MarkdownEditResult) {
            if (result.source != text?.toString().orEmpty()) {
                editableText.replace(0, editableText.length, result.source)
            }
            setSelection(
                result.selectionStart.coerceIn(0, length()),
                result.selectionEnd.coerceIn(0, length())
            )
        }

        private fun typeWithAutoPair(input: CharSequence?): Boolean {
            val value = input?.toString() ?: return false
            val result = MarkdownAutoPairing.type(
                text?.toString().orEmpty(),
                selectionStart.coerceAtLeast(0),
                selectionEnd.coerceAtLeast(0),
                value
            ) ?: return false
            applyEdit(result)
            return true
        }

        private fun deleteEmptyPair(): Boolean {
            if (selectionStart != selectionEnd) return false
            val result = MarkdownAutoPairing.deleteEmptyPair(
                text?.toString().orEmpty(),
                selectionStart.coerceAtLeast(0)
            ) ?: return false
            applyEdit(result)
            return true
        }

        private fun deleteAtStart(): Boolean {
            if (selectionStart != 0 || selectionEnd != 0) return false
            return onDeleteAtStart?.invoke() == true
        }

        override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
            if (keyCode == KeyEvent.KEYCODE_DEL) {
                if (deleteEmptyPair() || deleteAtStart()) return true
            } else if (!event.isCtrlPressed && !event.isAltPressed) {
                val unicode = event.unicodeChar
                if (unicode > 0 && typeWithAutoPair(unicode.toChar().toString())) return true
            }
            return super.onKeyDown(keyCode, event)
        }

        override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
            val target = super.onCreateInputConnection(outAttrs) ?: return null
            return object : InputConnectionWrapper(target, false) {
                override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                    if (typeWithAutoPair(text)) return true
                    return super.commitText(text, newCursorPosition)
                }

                override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                    if (beforeLength == 1 && afterLength == 0 && deleteEmptyPair()) return true
                    if (beforeLength > 0 && deleteAtStart()) return true
                    return super.deleteSurroundingText(beforeLength, afterLength)
                }

                override fun deleteSurroundingTextInCodePoints(
                    beforeLength: Int,
                    afterLength: Int
                ): Boolean {
                    if (beforeLength == 1 && afterLength == 0 && deleteEmptyPair()) return true
                    if (beforeLength > 0 && deleteAtStart()) return true
                    return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
                }
            }
        }
    }

    private class TextHolder(val text: TextView) : RecyclerView.ViewHolder(text)

    private class AssetHolder(container: LinearLayout) : RecyclerView.ViewHolder(container) {
        private val header = LinearLayout(container.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val caption = TextView(container.context).apply {
            setTextColor(ThemeColors.resolve(container.context, R.attr.inkNoteTextSecondary))
            textSize = 13f
        }
        val menu = AppCompatImageButton(container.context).apply {
            setImageResource(R.drawable.ic_more_vert_24)
            setColorFilter(ThemeColors.resolve(container.context, R.attr.inkNoteIconColor))
            contentDescription = container.context.getString(R.string.asset_actions)
            val backgroundValue = TypedValue()
            container.context.theme.resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless,
                backgroundValue,
                true
            )
            setBackgroundResource(backgroundValue.resourceId)
        }
        val documentToggle = AppCompatImageButton(container.context).apply {
            setColorFilter(ThemeColors.resolve(container.context, R.attr.inkNoteIconColor))
            val backgroundValue = TypedValue()
            container.context.theme.resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless,
                backgroundValue,
                true
            )
            setBackgroundResource(backgroundValue.resourceId)
        }
        val image = ImageView(container.context).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(ThemeColors.resolve(container.context, R.attr.inkNotePreviewBackground))
            contentDescription = "Embedded note asset"
        }
        val pageNote = TextView(container.context).apply {
            setTextColor(ThemeColors.resolve(container.context, androidx.appcompat.R.attr.colorPrimary))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, 0)
        }

        init {
            header.addView(caption, LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ))
            header.addView(documentToggle, LinearLayout.LayoutParams(
                (40 * container.resources.displayMetrics.density).toInt(),
                (40 * container.resources.displayMetrics.density).toInt()
            ))
            header.addView(menu, LinearLayout.LayoutParams(
                (40 * container.resources.displayMetrics.density).toInt(),
                (40 * container.resources.displayMetrics.density).toInt()
            ))
            container.addView(header, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            container.addView(image, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            container.addView(pageNote, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
    }

    private companion object {
        const val TYPE_EDITOR = 0
        const val TYPE_MARKDOWN = 1
        const val TYPE_IMAGE = 2
        const val TYPE_PDF = 3
        const val TYPE_ERROR = 4
        const val TYPE_ATTACHMENT = 5
        const val TYPE_END_ZONE = 6
    }

    private data class PendingEdit(
        val lineIndex: Int,
        val transform: (String, Int, Int) -> MarkdownEditResult
    )
}
