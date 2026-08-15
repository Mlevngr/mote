package com.mlevngr.inknote.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.recyclerview.widget.RecyclerView
import com.mlevngr.inknote.pdf.PdfDocumentSource
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import java.io.Closeable
import java.io.File
import java.util.concurrent.Executors

class PreviewAdapter(private val context: Context) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>(), Closeable {

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
    private var rows: List<PreviewRow> = emptyList()
    private val horizontalPadding = dp(18)
    private val targetWidth get() = context.resources.displayMetrics.widthPixels - dp(36)

    fun submit(newRows: List<PreviewRow>) {
        rows = newRows
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is PreviewRow.Markdown -> TYPE_MARKDOWN
        is PreviewRow.Image -> TYPE_IMAGE
        is PreviewRow.PdfPage -> TYPE_PDF
        is PreviewRow.Error -> TYPE_ERROR
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (viewType) {
            TYPE_MARKDOWN, TYPE_ERROR -> TextHolder(TextView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(horizontalPadding, dp(8), horizontalPadding, dp(8))
                textSize = 17f
                setTextIsSelectable(true)
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
            is PreviewRow.Markdown -> {
                holder as TextHolder
                holder.text.setTextColor(Color.rgb(35, 35, 40))
                markwon.setMarkdown(holder.text, row.source)
            }
            is PreviewRow.Error -> {
                holder as TextHolder
                holder.text.setTextColor(Color.rgb(180, 45, 45))
                holder.text.text = row.message
            }
            is PreviewRow.Image -> bindImage(holder as AssetHolder, row)
            is PreviewRow.PdfPage -> bindPdf(holder as AssetHolder, row)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is AssetHolder) {
            holder.image.tag = null
            holder.image.setImageDrawable(null)
        }
    }

    private fun bindImage(holder: AssetHolder, row: PreviewRow.Image) {
        holder.caption.text = row.label.ifBlank { row.file.name }
        val key = "image:${row.file.path}:${row.file.lastModified()}:$targetWidth"
        loadBitmap(holder, key) { decodeImage(row.file, targetWidth) }
    }

    private fun bindPdf(holder: AssetHolder, row: PreviewRow.PdfPage) {
        holder.caption.text = "${row.label}  •  ${row.pageIndex + 1}/${row.pageCount}"
        val key = "pdf:${row.file.path}:${row.file.lastModified()}:${row.pageIndex}:$targetWidth"
        loadBitmap(holder, key) {
            val source = synchronized(pdfSources) {
                pdfSources.getOrPut(row.file.path) { PdfDocumentSource(row.file) }
            }
            source.render(row.pageIndex, targetWidth)
        }
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
            main.post {
                if (holder.image.tag == key) holder.image.setImageBitmap(bitmap)
            }
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

    private class TextHolder(val text: TextView) : RecyclerView.ViewHolder(text)

    private class AssetHolder(container: LinearLayout) : RecyclerView.ViewHolder(container) {
        val caption = TextView(container.context).apply {
            setTextColor(Color.rgb(90, 90, 100))
            textSize = 13f
            setPadding(0, 0, 0, (6 * resources.displayMetrics.density).toInt())
        }
        val image = ImageView(container.context).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.rgb(245, 245, 247))
            contentDescription = "Embedded note asset"
        }

        init {
            container.addView(caption, LinearLayout.LayoutParams(
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
        const val TYPE_MARKDOWN = 0
        const val TYPE_IMAGE = 1
        const val TYPE_PDF = 2
        const val TYPE_ERROR = 3
    }
}
