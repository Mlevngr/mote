package com.mlevngr.inknote.ui

import android.content.Context
import android.content.res.Configuration
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.mlevngr.inknote.R
import com.mlevngr.inknote.appearance.AppearancePreferences
import com.mlevngr.inknote.appearance.ThemeColors
import com.mlevngr.inknote.appearance.ThemePalette
import com.mlevngr.inknote.library.FolderColor
import com.mlevngr.inknote.library.NoteLibrary
import kotlin.math.ceil

class TrashAdapter(
    private val context: Context,
    private val retentionMillis: () -> Long,
    private val onActions: (NoteLibrary.TrashEntry) -> Unit
) : RecyclerView.Adapter<TrashAdapter.Holder>() {
    private val appTheme = AppearancePreferences(context).theme
    private var entries: List<NoteLibrary.TrashEntry> = emptyList()

    fun submit(entries: List<NoteLibrary.TrashEntry>) {
        val previous = this.entries
        this.entries = entries
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = previous.size
            override fun getNewListSize(): Int = entries.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                previous[oldItemPosition].id == entries[newItemPosition].id

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                previous[oldItemPosition] == entries[newItemPosition]
        }).dispatchUpdatesTo(this)
    }

    override fun getItemCount(): Int = entries.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(8), dp(12), dp(8))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(78))
            foreground = selectable(false)
        }
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(entries[position])

    inner class Holder(container: LinearLayout) : RecyclerView.ViewHolder(container) {
        private val icon = ImageView(context).also {
            container.addView(it, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(16) })
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
            setTextColor(color(R.attr.inkNoteTextSecondary)); textSize = 12f; maxLines = 2
            labels.addView(this)
        }
        private val menu = ImageButton(context).apply {
            setImageResource(R.drawable.ic_more_vert_24)
            setColorFilter(color(R.attr.inkNoteTextSecondary))
            background = selectable(true)
            contentDescription = context.getString(R.string.entry_actions)
            container.addView(this, LinearLayout.LayoutParams(dp(48), dp(48)))
        }

        fun bind(entry: NoteLibrary.TrashEntry) {
            val isFolder = entry.type == NoteLibrary.EntryType.Folder
            icon.setImageResource(if (isFolder) R.drawable.ic_folder_24 else R.drawable.ic_note_24)
            icon.setColorFilter(if (isFolder) folderColor(entry.folderColor) else color(androidx.appcompat.R.attr.colorPrimary))
            title.text = entry.name
            val remaining = (entry.deletedAt + retentionMillis() - System.currentTimeMillis()).coerceAtLeast(0)
            val days = ceil(remaining / DAY_MILLIS.toDouble()).toInt()
            val deleted = DateUtils.getRelativeTimeSpanString(
                entry.deletedAt,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )
            val original = entry.originalLocation.displayPath.ifBlank { context.getString(R.string.library_root) }
            subtitle.text = context.getString(R.string.trash_entry_summary, deleted, original, days)
            itemView.setOnClickListener { onActions(entry) }
            itemView.setOnLongClickListener { onActions(entry); true }
            menu.setOnClickListener { onActions(entry) }
        }
    }

    private fun folderColor(folderColor: FolderColor): Int {
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        return ThemePalette.folderColor(appTheme, folderColor, night)
    }

    private fun selectable(borderless: Boolean) = TypedValue().also {
        context.theme.resolveAttribute(
            if (borderless) android.R.attr.selectableItemBackgroundBorderless
            else android.R.attr.selectableItemBackground,
            it,
            true
        )
    }.let { AppCompatResources.getDrawable(context, it.resourceId) }

    private fun color(attribute: Int): Int = ThemeColors.resolve(context, attribute)
    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val DAY_MILLIS = 24L * 60L * 60L * 1_000L
    }
}
