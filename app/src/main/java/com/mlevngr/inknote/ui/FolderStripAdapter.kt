package com.mlevngr.inknote.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.mlevngr.inknote.R
import com.mlevngr.inknote.appearance.AppearancePreferences
import com.mlevngr.inknote.appearance.ThemeColors
import com.mlevngr.inknote.appearance.ThemePalette
import com.mlevngr.inknote.library.FolderColor
import com.mlevngr.inknote.library.NoteLibrary
import kotlin.math.roundToInt

class FolderStripAdapter(
    private val context: Context,
    private val onClick: (NoteLibrary.Entry) -> Unit,
    private val onMenu: (NoteLibrary.Entry) -> Unit
) : RecyclerView.Adapter<FolderStripAdapter.Holder>() {
    private val appTheme = AppearancePreferences(context).theme
    private var folders = emptyList<NoteLibrary.Entry>()

    init {
        setHasStableIds(true)
    }

    fun submit(updated: List<NoteLibrary.Entry>) {
        val previous = folders
        folders = updated
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = previous.size
            override fun getNewListSize(): Int = updated.size
            override fun areItemsTheSame(oldPosition: Int, newPosition: Int): Boolean =
                previous[oldPosition].relativePath == updated[newPosition].relativePath

            override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean =
                previous[oldPosition] == updated[newPosition]
        }).dispatchUpdatesTo(this)
    }

    override fun getItemId(position: Int): Long = folders[position].relativePath.hashCode().toLong()

    override fun getItemCount(): Int = folders.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val viewportWidth = parent.measuredWidth.takeIf { it > 0 }
            ?: context.resources.displayMetrics.widthPixels
        val cardWidth = FolderStripSizing.cardWidth(
            viewportWidthPx = viewportWidth,
            paddingStartPx = parent.paddingStart,
            paddingEndPx = parent.paddingEnd,
            horizontalMarginsPx = dp(12)
        )
        return Holder(createCard(cardWidth))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(folders[position])

    private fun createCard(width: Int): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = RecyclerView.LayoutParams(width, dp(126)).apply {
            marginStart = dp(6)
            marginEnd = dp(6)
        }
        setPadding(dp(16), dp(15), dp(12), dp(12))
        foreground = selectable(false)
        clipToOutline = true
    }

    private fun selectable(borderless: Boolean) = TypedValue().also {
        context.theme.resolveAttribute(
            if (borderless) android.R.attr.selectableItemBackgroundBorderless
            else android.R.attr.selectableItemBackground,
            it,
            true
        )
    }.let { AppCompatResources.getDrawable(context, it.resourceId) }

    private fun rounded(fill: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusDp * context.resources.displayMetrics.density
        setColor(fill)
    }

    private fun folderColor(color: FolderColor): Int {
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        return ThemePalette.folderColor(appTheme, color, night)
    }

    private fun color(attribute: Int): Int = ThemeColors.resolve(context, attribute)

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()

    inner class Holder(private val container: LinearLayout) : RecyclerView.ViewHolder(container) {
        private val top = FrameLayout(context).also {
            container.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        private val icon = ImageView(context).also {
            it.setImageResource(R.drawable.ic_folder_24)
            top.addView(it, FrameLayout.LayoutParams(dp(36), dp(36), Gravity.START or Gravity.CENTER_VERTICAL))
        }
        private val menu = ImageButton(context).also {
            it.setImageResource(R.drawable.ic_more_vert_24)
            it.setColorFilter(color(R.attr.inkNoteTextSecondary))
            it.background = selectable(true)
            it.contentDescription = context.getString(R.string.entry_actions)
            top.addView(it, FrameLayout.LayoutParams(dp(42), dp(42), Gravity.END or Gravity.TOP))
        }
        private val title = TextView(context).also {
            it.setTextColor(color(R.attr.inkNoteTextPrimary))
            it.textSize = 16f
            it.maxLines = 1
            container.addView(it)
        }
        private val count = TextView(context).also {
            it.setTextColor(color(R.attr.inkNoteTextSecondary))
            it.textSize = 12f
            it.maxLines = 1
            container.addView(it)
        }

        fun bind(folder: NoteLibrary.Entry) {
            val accent = folderColor(folder.folderColor)
            container.background = rounded(
                ColorUtils.blendARGB(color(R.attr.inkNoteCardBackground), accent, 0.12f),
                18f
            )
            icon.setColorFilter(accent)
            title.text = folder.name
            count.text = context.resources.getQuantityString(
                R.plurals.folder_item_count,
                folder.childCount,
                folder.childCount
            )
            container.setOnClickListener { onClick(folder) }
            container.setOnLongClickListener { onMenu(folder); true }
            menu.setOnClickListener { onMenu(folder) }
        }
    }
}

internal object FolderStripSizing {
    private const val VISIBLE_CARD_SLOTS = 2.3f

    fun cardWidth(
        viewportWidthPx: Int,
        paddingStartPx: Int,
        paddingEndPx: Int,
        horizontalMarginsPx: Int
    ): Int {
        val available = (viewportWidthPx - paddingStartPx - paddingEndPx).coerceAtLeast(1)
        return ((available / VISIBLE_CARD_SLOTS).roundToInt() - horizontalMarginsPx).coerceAtLeast(1)
    }
}
