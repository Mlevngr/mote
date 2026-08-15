package com.mlevngr.inknote.ui

import android.content.Context
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.RecyclerView
import com.mlevngr.inknote.R
import com.mlevngr.inknote.library.NoteLibrary

class NoteLibraryAdapter(
    private val context: Context,
    private val onClick: (NoteLibrary.Entry) -> Unit,
    private val onMenu: (NoteLibrary.Entry) -> Unit
) : RecyclerView.Adapter<NoteLibraryAdapter.EntryHolder>() {
    private var entries: List<NoteLibrary.Entry> = emptyList()

    fun submit(entries: List<NoteLibrary.Entry>) {
        this.entries = entries
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = entries.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntryHolder {
        val selectable = TypedValue().also {
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        }.resourceId
        return EntryHolder(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72))
            setPadding(dp(20), dp(8), dp(20), dp(8))
            setBackgroundResource(selectable)
        })
    }

    override fun onBindViewHolder(holder: EntryHolder, position: Int) {
        val entry = entries[position]
        holder.icon.setImageResource(
            if (entry.type == NoteLibrary.EntryType.Folder) R.drawable.ic_folder_24
            else R.drawable.ic_note_24
        )
        holder.icon.setColorFilter(context.getColor(R.color.primary))
        holder.title.text = entry.name
        holder.subtitle.text = if (entry.type == NoteLibrary.EntryType.Folder) {
            context.getString(R.string.folder)
        } else {
            DateUtils.getRelativeTimeSpanString(
                entry.modifiedAt,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )
        }
        holder.itemView.setOnClickListener { onClick(entry) }
        holder.itemView.setOnLongClickListener {
            onMenu(entry)
            true
        }
        holder.menu.setOnClickListener { onMenu(entry) }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    inner class EntryHolder(container: LinearLayout) : RecyclerView.ViewHolder(container) {
        val icon = ImageView(context).apply {
            contentDescription = null
            container.addView(this, LinearLayout.LayoutParams(dp(32), dp(32)).apply {
                marginEnd = dp(16)
            })
        }
        private val labels = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            container.addView(this, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        val title = TextView(context).apply {
            setTextColor(context.getColor(R.color.text_primary))
            textSize = 17f
            maxLines = 1
            labels.addView(this)
        }
        val subtitle = TextView(context).apply {
            setTextColor(context.getColor(R.color.text_secondary))
            textSize = 13f
            maxLines = 1
            labels.addView(this)
        }
        val menu = ImageButton(context).apply {
            setImageResource(R.drawable.ic_more_vert_24)
            setColorFilter(context.getColor(R.color.text_secondary))
            background = TypedValue().also {
                context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
            }.let { AppCompatResources.getDrawable(context, it.resourceId) }
            contentDescription = context.getString(R.string.entry_actions)
            container.addView(this, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
    }
}
