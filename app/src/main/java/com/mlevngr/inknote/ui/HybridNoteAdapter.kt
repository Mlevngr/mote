package com.mlevngr.inknote.ui

import android.content.ClipboardManager
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
import android.view.ActionMode
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
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
import com.mlevngr.inknote.pdf.PdfDocumentSource
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
    private val onLongActivate: (Int) -> Unit,
    private val onLineChanged: (Int, String) -> Unit,
    private val onSplitLine: (Int, Int) -> Unit,
    private val onMultilineInput: (Int, String, Int) -> Unit,
    private val onMergeWithPrevious: (Int) -> Boolean,
    private val onAssetActions: (Int, File, String) -> Unit,
    private val onPasteAt: (Int) -> Unit,
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
    private val collapsedAssetPaths = mutableSetOf<String>()
    private var focusLine: Int? = null
    private var focusCursor: Int? = null
    private var editing = false
    private val horizontalPadding = dp(18)
    private val targetWidth get() = context.resources.displayMetrics.widthPixels - dp(36)

    fun submit(
        newRows: List<HybridRow>,
        editing: Boolean,
        focusLine: Int? = null,
        focusCursor: Int? = null
    ) {
        allRows = newRows
        val availableAssets = newRows.mapNotNull(AssetPreviewVisibility::assetFile)
            .mapTo(mutableSetOf()) { it.canonicalPath }
        collapsedAssetPaths.retainAll(availableAssets)
        rebuildVisibleRows()
        this.editing = editing
        this.focusLine = focusLine
        this.focusCursor = focusCursor
        notifyDataSetChanged()
    }

    fun positionOfLine(lineIndex: Int): Int = rows.indexOfFirst { it.lineIndex == lineIndex }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int = when (val row = rows[position]) {
        is HybridRow.Editor -> TYPE_EDITOR
        is HybridRow.Rendered -> when (row.preview) {
            is PreviewRow.Markdown -> TYPE_MARKDOWN
            is PreviewRow.Image -> TYPE_IMAGE
            is PreviewRow.PdfPage -> TYPE_PDF
            is PreviewRow.Attachment -> TYPE_ATTACHMENT
            is PreviewRow.Error -> TYPE_ERROR
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
        when (val row = rows[position]) {
            is HybridRow.Editor -> bindEditor(holder as EditorHolder, row)
            is HybridRow.Rendered -> {
                holder.itemView.setOnClickListener(if (editing) {
                    { onActivate(row.lineIndex) }
                } else null)
                when (val preview = row.preview) {
                    is PreviewRow.Markdown -> {
                        holder as TextHolder
                        val blank = preview.source == "\u00a0"
                        holder.text.setTextIsSelectable(!editing && !blank)
                        holder.text.setOnClickListener(if (editing) {
                            { onActivate(row.lineIndex) }
                        } else null)
                        val pasteListener = if (blank) {
                            View.OnLongClickListener { onPasteAt(row.lineIndex); true }
                        } else null
                        holder.text.setOnLongClickListener(pasteListener)
                        holder.itemView.setOnLongClickListener(pasteListener)
                        holder.text.setTextColor(context.getColor(R.color.text_primary))
                        markwon.setMarkdown(holder.text, preview.source)
                    }
                    is PreviewRow.Attachment -> {
                        holder as TextHolder
                        holder.text.setTextIsSelectable(false)
                        bindAssetActions(
                            holder.itemView,
                            holder.text,
                            null,
                            row.lineIndex,
                            preview.file,
                            preview.label
                        )
                        holder.text.setTextColor(context.getColor(R.color.primary))
                        holder.text.text = "📎  ${preview.label}  •  ${formatSize(preview.file.length())}"
                    }
                    is PreviewRow.Error -> {
                        holder as TextHolder
                        holder.text.setTextIsSelectable(false)
                        holder.text.setOnLongClickListener(null)
                        holder.itemView.setOnLongClickListener(if (!editing) {
                            { onLongActivate(row.lineIndex); true }
                        } else null)
                        holder.text.setTextColor(context.getColor(R.color.error_text))
                        holder.text.text = preview.message
                    }
                    is PreviewRow.Image -> {
                        holder as AssetHolder
                        bindAssetActions(
                            holder.itemView,
                            holder.caption,
                            holder.menu,
                            row.lineIndex,
                            preview.file,
                            preview.label.ifBlank { preview.file.name }
                        )
                        bindImage(holder, preview)
                    }
                    is PreviewRow.PdfPage -> {
                        holder as AssetHolder
                        bindAssetActions(
                            holder.itemView,
                            holder.caption,
                            holder.menu,
                            row.lineIndex,
                            preview.file,
                            preview.label.ifBlank { preview.file.name }
                        )
                        bindPdf(holder, preview)
                    }
                }
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        holder.itemView.setOnClickListener(null)
        holder.itemView.setOnLongClickListener(null)
        if (holder is EditorHolder) holder.detach()
        if (holder is AssetHolder) {
            holder.caption.setOnClickListener(null)
            holder.caption.setOnLongClickListener(null)
            holder.menu.setOnClickListener(null)
            holder.image.tag = null
            holder.image.setImageDrawable(null)
        }
    }

    private fun bindEditor(holder: EditorHolder, row: HybridRow.Editor) {
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
            focusCursor = null
            holder.editor.post {
                holder.editor.requestFocus()
                holder.editor.setSelection(
                    (cursor ?: holder.editor.text?.length ?: 0)
                        .coerceIn(0, holder.editor.text?.length ?: 0)
                )
                context.getSystemService<InputMethodManager>()
                    ?.showSoftInput(holder.editor, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun bindImage(holder: AssetHolder, row: PreviewRow.Image) {
        val collapsed = isCollapsed(row.file)
        bindAssetHeader(holder, row.file, row.label.ifBlank { row.file.name }, collapsed)
        holder.image.visibility = if (collapsed) View.GONE else View.VISIBLE
        if (collapsed) {
            holder.image.tag = null
            holder.image.setImageDrawable(null)
            return
        }
        val key = "image:${row.file.path}:${row.file.lastModified()}:$targetWidth"
        loadBitmap(holder, key) { decodeImage(row.file, targetWidth) }
    }

    private fun bindPdf(holder: AssetHolder, row: PreviewRow.PdfPage) {
        val collapsed = isCollapsed(row.file)
        val pageLabel = if (collapsed) {
            "${row.label}  •  ${context.getString(R.string.pdf_page_count, row.pageCount)}"
        } else {
            "${row.label}  •  ${row.pageIndex + 1}/${row.pageCount}"
        }
        bindAssetHeader(holder, row.file, pageLabel, collapsed)
        holder.image.visibility = if (collapsed) View.GONE else View.VISIBLE
        if (collapsed) {
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
        file: File,
        label: String,
        collapsed: Boolean
    ) {
        holder.caption.text = "${if (collapsed) '▶' else '▼'}  $label"
        holder.caption.contentDescription = context.getString(
            if (collapsed) R.string.expand_preview else R.string.collapse_preview,
            label
        )
        holder.caption.setOnClickListener { toggleAsset(file) }
    }

    private fun bindAssetActions(
        itemView: View,
        actionView: View,
        menuView: View?,
        lineIndex: Int,
        file: File,
        label: String
    ) {
        val listener = View.OnLongClickListener {
            onAssetActions(lineIndex, file, label)
            true
        }
        itemView.setOnLongClickListener(listener)
        actionView.setOnLongClickListener(listener)
        menuView?.setOnClickListener { onAssetActions(lineIndex, file, label) }
    }

    private fun isCollapsed(file: File): Boolean = file.canonicalPath in collapsedAssetPaths

    private fun toggleAsset(file: File) {
        val key = file.canonicalPath
        if (!collapsedAssetPaths.add(key)) collapsedAssetPaths.remove(key)
        rebuildVisibleRows()
        notifyDataSetChanged()
    }

    private fun rebuildVisibleRows() {
        rows = AssetPreviewVisibility.visibleRows(allRows, collapsedAssetPaths)
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
            onChanged: (Int, String) -> Unit,
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
                    if ('\n' !in value && '\r' !in value) onChanged(lineIndex, value)
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

        private val selectionAction = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.add(0, android.R.id.cut, 0, R.string.cut_text)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                menu.add(0, android.R.id.copy, 1, R.string.copy_text)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                menu.add(0, android.R.id.paste, 2, R.string.paste)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                menu.add(0, android.R.id.selectAll, 3, R.string.select_all_text)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.findItem(android.R.id.paste)?.isEnabled =
                    context.getSystemService<ClipboardManager>()?.hasPrimaryClip() == true
                return true
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean =
                when (item.itemId) {
                    android.R.id.selectAll -> {
                        setSelection(0, text?.length ?: 0)
                        true
                    }
                    android.R.id.cut, android.R.id.copy, android.R.id.paste -> {
                        val handled = onTextContextMenuItem(item.itemId)
                        if (handled) mode.finish()
                        handled
                    }
                    else -> false
                }

            override fun onDestroyActionMode(mode: ActionMode) = Unit
        }

        private fun deleteAtStart(): Boolean {
            if (selectionStart != 0 || selectionEnd != 0) return false
            return onDeleteAtStart?.invoke() == true
        }

        override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
            if (keyCode == KeyEvent.KEYCODE_DEL && deleteAtStart()) return true
            return super.onKeyDown(keyCode, event)
        }

        override fun performLongClick(): Boolean =
            showSelectionActions(selectionStart.coerceAtLeast(0))

        override fun performLongClick(x: Float, y: Float): Boolean =
            showSelectionActions(textOffset(x, y))

        private fun showSelectionActions(offset: Int): Boolean {
            val value = text?.toString().orEmpty()
            val range = TextSelection.wordAt(value, offset) ?: return super.performLongClick()
            setSelection(range.first, range.last + 1)
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            startActionMode(selectionAction, ActionMode.TYPE_FLOATING)
            return true
        }

        private fun textOffset(x: Float, y: Float): Int {
            val textLayout = layout ?: return selectionStart.coerceAtLeast(0)
            val vertical = (y - totalPaddingTop + scrollY).toInt().coerceAtLeast(0)
            val line = textLayout.getLineForVertical(vertical)
            val horizontal = x - totalPaddingLeft + scrollX
            return textLayout.getOffsetForHorizontal(line, horizontal)
        }

        override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
            val target = super.onCreateInputConnection(outAttrs) ?: return null
            return object : InputConnectionWrapper(target, false) {
                override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                    if (beforeLength > 0 && deleteAtStart()) return true
                    return super.deleteSurroundingText(beforeLength, afterLength)
                }

                override fun deleteSurroundingTextInCodePoints(
                    beforeLength: Int,
                    afterLength: Int
                ): Boolean {
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
            setTextColor(container.context.getColor(R.color.text_secondary))
            textSize = 13f
        }
        val menu = AppCompatImageButton(container.context).apply {
            setImageResource(R.drawable.ic_more_vert_24)
            setColorFilter(container.context.getColor(R.color.text_secondary))
            contentDescription = container.context.getString(R.string.asset_actions)
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
            setBackgroundColor(container.context.getColor(R.color.preview_background))
            contentDescription = "Embedded note asset"
        }

        init {
            header.addView(caption, LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
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
        }
    }

    private companion object {
        const val TYPE_EDITOR = 0
        const val TYPE_MARKDOWN = 1
        const val TYPE_IMAGE = 2
        const val TYPE_PDF = 3
        const val TYPE_ERROR = 4
        const val TYPE_ATTACHMENT = 5
    }
}
