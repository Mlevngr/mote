package com.mlevngr.inknote.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import com.mlevngr.inknote.R
import com.mlevngr.inknote.appearance.LibraryLayoutMode
import com.mlevngr.inknote.appearance.NotePreviewMode
import com.mlevngr.inknote.appearance.ThemeColors
import com.mlevngr.inknote.library.FolderColor
import com.mlevngr.inknote.library.NoteLibrary
import java.io.Closeable
import java.util.concurrent.Executors

class NoteLibraryAdapter(
    private val context: Context,
    private val onClick: (NoteLibrary.Entry) -> Unit,
    private val onMenu: (NoteLibrary.Entry) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), Closeable {
    private var items: List<LibraryItem> = emptyList()
    private var layoutMode = LibraryLayoutMode.Samsung
    private var previewMode = NotePreviewMode.RenderedPage
    private val thumbnailExecutor = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private val imageCache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val thumbnailRenderer = NotePageThumbnailRenderer(
        pageColor = color(R.attr.inkNoteEditorBackground),
        textColor = color(R.attr.inkNoteTextPrimary),
        secondaryTextColor = color(R.attr.inkNoteTextSecondary),
        accentColor = color(androidx.appcompat.R.attr.colorPrimary)
    )

    fun submit(
        entries: List<NoteLibrary.Entry>,
        layoutMode: LibraryLayoutMode,
        previewMode: NotePreviewMode
    ) {
        val updated = LibraryItems.build(
            entries,
            layoutMode,
            context.getString(R.string.folders_section),
            context.getString(R.string.notes_section)
        )
        val previous = items
        val previousPreviewMode = this.previewMode
        this.layoutMode = layoutMode
        this.previewMode = previewMode
        items = updated
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = previous.size
            override fun getNewListSize(): Int = updated.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = previous[oldItemPosition]
                val new = updated[newItemPosition]
                return when {
                    old is LibraryItem.Header && new is LibraryItem.Header -> old.title == new.title
                    old is LibraryItem.EntryItem && new is LibraryItem.EntryItem ->
                        old.entry.relativePath == new.entry.relativePath && old.entry.type == new.entry.type
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                previousPreviewMode == previewMode && previous[oldItemPosition] == updated[newItemPosition]
        }).dispatchUpdatesTo(this)
    }

    fun spanSize(position: Int): Int = LibraryItems.spanSize(items[position], layoutMode)

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = when (val item = items[position]) {
        is LibraryItem.Header -> VIEW_HEADER
        is LibraryItem.EntryItem -> when (item.style) {
            EntryStyle.List -> VIEW_LIST
            EntryStyle.FolderCard -> VIEW_FOLDER_CARD
            EntryStyle.NoteCard -> VIEW_NOTE_CARD
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = when (viewType) {
        VIEW_HEADER -> HeaderHolder(TextView(context).apply {
            setTextColor(color(R.attr.inkNoteTextPrimary))
            textSize = 19f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(20), dp(22), dp(16), dp(10))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        VIEW_FOLDER_CARD -> FolderCardHolder(createFolderCard())
        VIEW_NOTE_CARD -> NoteCardHolder(createNoteCard())
        else -> ListHolder(createListRow())
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderHolder -> holder.text.text = (items[position] as LibraryItem.Header).title
            is ListHolder -> holder.bind((items[position] as LibraryItem.EntryItem).entry)
            is FolderCardHolder -> holder.bind((items[position] as LibraryItem.EntryItem).entry)
            is NoteCardHolder -> holder.bind((items[position] as LibraryItem.EntryItem).entry)
        }
    }

    override fun close() {
        thumbnailExecutor.shutdownNow()
        imageCache.evictAll()
    }

    private fun bindActions(view: View, menu: View, entry: NoteLibrary.Entry) {
        view.setOnClickListener { onClick(entry) }
        view.setOnLongClickListener { onMenu(entry); true }
        menu.setOnClickListener { onMenu(entry) }
    }

    private fun createListRow(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(84))
        setPadding(dp(16), dp(8), dp(12), dp(8))
        foreground = selectable(false)
    }

    private fun createFolderCard(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(126)).apply {
            marginStart = dp(8); marginEnd = dp(8); topMargin = dp(6); bottomMargin = dp(6)
        }
        setPadding(dp(16), dp(15), dp(12), dp(12))
        foreground = selectable(false)
    }

    private fun createNoteCard(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(238)).apply {
            marginStart = dp(8); marginEnd = dp(8); topMargin = dp(6); bottomMargin = dp(6)
        }
        background = rounded(color(R.attr.inkNoteCardBackground), 18f)
        foreground = selectable(false)
        clipToOutline = true
    }

    private fun iconButton(): ImageButton = ImageButton(context).apply {
        setImageResource(R.drawable.ic_more_vert_24)
        setColorFilter(color(R.attr.inkNoteTextSecondary))
        background = selectable(true)
        contentDescription = context.getString(R.string.entry_actions)
    }

    private fun selectable(borderless: Boolean) = TypedValue().also {
        context.theme.resolveAttribute(
            if (borderless) android.R.attr.selectableItemBackgroundBorderless
            else android.R.attr.selectableItemBackground,
            it,
            true
        )
    }.let { AppCompatResources.getDrawable(context, it.resourceId) }

    private fun rounded(fill: Int, radius: Float): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radius)
        setColor(fill)
    }

    private fun folderColor(color: FolderColor): Int {
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        return if (night) color.dark else color.light
    }

    private fun relativeTime(entry: NoteLibrary.Entry): CharSequence = DateUtils.getRelativeTimeSpanString(
        entry.modifiedAt,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
    )

    private fun loadPageThumbnail(entry: NoteLibrary.Entry, imageView: ImageView, width: Int, height: Int) {
        val key = "${entry.relativePath}:${entry.modifiedAt}:$width:$height:${thumbnailThemeKey()}"
        imageView.tag = key
        imageView.setImageDrawable(null)
        imageCache.get(key)?.let {
            imageView.clearColorFilter()
            imageView.scaleType = ImageView.ScaleType.FIT_XY
            imageView.setImageBitmap(it)
            return
        }
        imageView.scaleType = ImageView.ScaleType.CENTER
        imageView.setImageResource(R.drawable.ic_note_24)
        imageView.setColorFilter(color(androidx.appcompat.R.attr.colorPrimary))
        thumbnailExecutor.execute {
            val bitmap = thumbnailRenderer.render(entry, width, height)
            imageCache.put(key, bitmap)
            main.post {
                if (imageView.tag == key) {
                    imageView.clearColorFilter()
                    imageView.scaleType = ImageView.ScaleType.FIT_XY
                    imageView.setImageBitmap(bitmap)
                }
            }
        }
    }

    private fun thumbnailThemeKey(): String = listOf(
        color(R.attr.inkNoteEditorBackground),
        color(R.attr.inkNoteTextPrimary),
        color(R.attr.inkNoteTextSecondary),
        color(androidx.appcompat.R.attr.colorPrimary)
    ).joinToString(":")

    private fun color(attribute: Int): Int = ThemeColors.resolve(context, attribute)
    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
    private fun dp(value: Float): Float = value * context.resources.displayMetrics.density

    inner class HeaderHolder(val text: TextView) : RecyclerView.ViewHolder(text)

    inner class ListHolder(container: LinearLayout) : RecyclerView.ViewHolder(container) {
        private val accent = View(context).also {
            container.addView(it, LinearLayout.LayoutParams(dp(4), dp(48)).apply { marginEnd = dp(12) })
        }
        private val preview = ImageView(context).apply {
            background = rounded(color(R.attr.inkNoteCardBackground), 12f)
            clipToOutline = true
            container.addView(this, LinearLayout.LayoutParams(dp(58), dp(58)).apply { marginEnd = dp(14) })
        }
        private val labels = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            container.addView(this, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        private val title = TextView(context).apply {
            setTextColor(color(R.attr.inkNoteTextPrimary)); textSize = 17f; maxLines = 1
            labels.addView(this)
        }
        private val subtitle = TextView(context).apply {
            setTextColor(color(R.attr.inkNoteTextSecondary)); textSize = 13f; maxLines = 2
            labels.addView(this)
        }
        private val menu = iconButton().also { container.addView(it, LinearLayout.LayoutParams(dp(44), dp(44))) }

        fun bind(entry: NoteLibrary.Entry) {
            title.text = entry.name
            val isFolder = entry.type == NoteLibrary.EntryType.Folder
            accent.visibility = if (isFolder) View.VISIBLE else View.INVISIBLE
            accent.background = rounded(if (isFolder) folderColor(entry.folderColor) else Color.TRANSPARENT, 3f)
            subtitle.text = when {
                isFolder -> context.resources.getQuantityString(
                    R.plurals.folder_item_count, entry.childCount, entry.childCount
                )
                previewMode == NotePreviewMode.Summary && entry.preview.excerpt.isNotBlank() ->
                    entry.preview.excerpt
                else -> relativeTime(entry)
            }
            subtitle.visibility = if (!isFolder && previewMode == NotePreviewMode.TitleOnly) View.GONE else View.VISIBLE
            if (!isFolder && previewMode != NotePreviewMode.RenderedPage) {
                preview.visibility = View.GONE
            } else {
                preview.visibility = View.VISIBLE
                if (isFolder) {
                    preview.scaleType = ImageView.ScaleType.CENTER
                    preview.setImageResource(R.drawable.ic_folder_24)
                    preview.setColorFilter(folderColor(entry.folderColor))
                } else loadPageThumbnail(entry, preview, dp(58), dp(58))
            }
            bindActions(itemView, menu, entry)
        }
    }

    inner class FolderCardHolder(container: LinearLayout) : RecyclerView.ViewHolder(container) {
        private val top = FrameLayout(context).also {
            container.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        private val icon = ImageView(context).also { image ->
            image.setImageResource(R.drawable.ic_folder_24)
            top.addView(image, FrameLayout.LayoutParams(dp(36), dp(36), Gravity.START or Gravity.CENTER_VERTICAL))
        }
        private val menu = iconButton().also {
            top.addView(it, FrameLayout.LayoutParams(dp(42), dp(42), Gravity.END or Gravity.TOP))
        }
        private val title = TextView(context).apply {
            setTextColor(color(R.attr.inkNoteTextPrimary)); textSize = 16f; maxLines = 1
            container.addView(this)
        }
        private val count = TextView(context).apply {
            setTextColor(color(R.attr.inkNoteTextSecondary)); textSize = 12f; maxLines = 1
            container.addView(this)
        }

        fun bind(entry: NoteLibrary.Entry) {
            val accent = folderColor(entry.folderColor)
            itemView.background = rounded(
                ColorUtils.blendARGB(color(R.attr.inkNoteCardBackground), accent, 0.12f),
                18f
            )
            icon.setColorFilter(accent)
            title.text = entry.name
            count.text = context.resources.getQuantityString(R.plurals.folder_item_count, entry.childCount, entry.childCount)
            bindActions(itemView, menu, entry)
        }
    }

    inner class NoteCardHolder(container: LinearLayout) : RecyclerView.ViewHolder(container) {
        private val image = ImageView(context).apply {
            setBackgroundColor(color(R.attr.inkNoteEditorBackground))
            container.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(142)))
        }
        private val details = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(6), dp(8))
            container.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        private val labels = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            details.addView(this, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        private val title = TextView(context).apply {
            setTextColor(color(R.attr.inkNoteTextPrimary)); textSize = 16f; maxLines = 1
            labels.addView(this)
        }
        private val subtitle = TextView(context).apply {
            setTextColor(color(R.attr.inkNoteTextSecondary)); textSize = 12f; maxLines = 1
            labels.addView(this)
        }
        private val menu = iconButton().also { details.addView(it, LinearLayout.LayoutParams(dp(42), dp(42))) }

        fun bind(entry: NoteLibrary.Entry) {
            title.text = entry.name
            subtitle.text = when {
                previewMode == NotePreviewMode.Summary && entry.preview.excerpt.isNotBlank() ->
                    entry.preview.excerpt
                else -> relativeTime(entry)
            }
            subtitle.visibility = if (previewMode == NotePreviewMode.TitleOnly) View.GONE else View.VISIBLE
            subtitle.maxLines = if (previewMode == NotePreviewMode.Summary) 6 else 1
            image.visibility = if (previewMode == NotePreviewMode.RenderedPage) View.VISIBLE else View.GONE
            if (previewMode == NotePreviewMode.RenderedPage) {
                loadPageThumbnail(
                    entry,
                    image,
                    (context.resources.displayMetrics.widthPixels - dp(48)) / 2,
                    dp(142)
                )
            }
            (itemView.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                params.height = dp(when (previewMode) {
                    NotePreviewMode.RenderedPage -> 238
                    NotePreviewMode.Summary -> 170
                    NotePreviewMode.TitleOnly -> 96
                })
                itemView.layoutParams = params
            }
            bindActions(itemView, menu, entry)
        }
    }

    private companion object {
        const val VIEW_HEADER = 0
        const val VIEW_LIST = 1
        const val VIEW_FOLDER_CARD = 2
        const val VIEW_NOTE_CARD = 3
    }
}
