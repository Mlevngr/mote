package com.mlevngr.inknote

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.mlevngr.inknote.appearance.AppearancePreferences
import com.mlevngr.inknote.appearance.Skin
import com.mlevngr.inknote.appearance.SkinColor
import com.mlevngr.inknote.appearance.SkinJson
import com.mlevngr.inknote.appearance.SkinPalette
import com.mlevngr.inknote.appearance.SkinPreferences
import com.mlevngr.inknote.appearance.SkinnableActivity
import com.mlevngr.inknote.appearance.ThemeColors
import com.mlevngr.inknote.ui.SystemBarInsets
import java.util.concurrent.Executors

class SkinSettingsActivity : SkinnableActivity() {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var nameInput: TextInputEditText
    private lateinit var colorFields: LinearLayout
    private lateinit var preview: MaterialCardView
    private lateinit var previewBody: TextView
    private lateinit var lightButton: MaterialButton
    private lateinit var darkButton: MaterialButton
    private val previewHeadings = mutableListOf<TextView>()
    private var light = mutableListOf<String>()
    private var dark = mutableListOf<String>()
    private var editDark = false
    private var pendingExport: String? = null

    private val importSkin = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) io.execute {
            val result = runCatching {
                requireNotNull(contentResolver.openInputStream(uri)) { "无法读取皮肤包" }.use(SkinJson::read)
            }
            main.post {
                if (isDestroyed || isFinishing) return@post
                result.onSuccess { skin ->
                    loadDraft(skin)
                    showColorFields()
                    toast(getString(R.string.skin_imported))
                }.onFailure(::showError)
            }
        }
    }
    private val exportSkin = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val json = pendingExport
        pendingExport = null
        if (uri != null && json != null) io.execute {
            val result = runCatching {
                requireNotNull(contentResolver.openOutputStream(uri, "wt")) { "无法写入皮肤包" }
                    .bufferedWriter(Charsets.UTF_8).use { it.write(json) }
            }
            main.post {
                if (isDestroyed || isFinishing) return@post
                result.onSuccess { toast(getString(R.string.skin_exported)) }.onFailure(::showError)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppearancePreferences(this).applyTheme(this)
        super.onCreate(savedInstanceState)
        title = getString(R.string.skin_settings)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.attr.inkNoteAppBackground))
        }
        val toolbar = MaterialToolbar(this).apply {
            setTitle(R.string.skin_settings)
            setTitleTextColor(color(R.attr.inkNoteTextPrimary))
            setBackgroundColor(color(R.attr.inkNoteToolbarBackground))
            setNavigationIcon(R.drawable.ic_arrow_back_24)
            setNavigationIconTint(color(R.attr.inkNoteIconColor))
            setNavigationContentDescription(R.string.back_to_library)
            setNavigationOnClickListener { finish() }
        }
        root.addView(toolbar, LinearLayout.LayoutParams(-1, 56.dp))
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 8.dp, 20.dp, 24.dp)
        }
        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(TextView(this).apply {
            setText(R.string.skin_explanation)
            setTextColor(color(R.attr.inkNoteTextSecondary))
            textSize = 14f
            setPadding(0, 0, 0, 12.dp)
        })
        if (Build.VERSION.SDK_INT < 30) content.addView(TextView(this).apply {
            setText(R.string.skin_legacy_notice)
            setTextColor(color(R.attr.inkNoteTextSecondary))
        })
        val transfers = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        transfers.addView(button(R.string.skin_import) {
            runCatching { importSkin.launch(arrayOf("*/*")) }.onFailure(::showError)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        transfers.addView(button(R.string.skin_export) {
            runCatching {
                pendingExport = SkinJson.encode(draft())
                exportSkin.launch("mote-skin.json")
            }.onFailure { pendingExport = null; showError(it) }
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = 8.dp })
        content.addView(transfers)

        nameInput = TextInputEditText(this).apply { setSingleLine(); background = null; id = View.generateViewId() }
        content.addView(TextInputLayout(this).apply {
            hint = getString(R.string.skin_name)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            addView(nameInput, LinearLayout.LayoutParams(-1, -2))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12.dp })
        val mode = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        lightButton = button(R.string.skin_light) { editDark = false; showColorFields() }
        darkButton = button(R.string.skin_dark) { editDark = true; showColorFields() }
        mode.addView(lightButton, LinearLayout.LayoutParams(0, -2, 1f))
        mode.addView(darkButton, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = 8.dp })
        content.addView(mode)

        preview = MaterialCardView(this).apply { radius = 16.dp.toFloat(); strokeWidth = 1.dp; cardElevation = 0f }
        val previewContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp, 14.dp, 18.dp, 14.dp)
        }
        repeat(6) { index ->
            TextView(this).apply {
                text = getString(R.string.skin_heading_preview, index + 1)
                textSize = 24f - index * 1.5f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                previewHeadings += this
                previewContent.addView(this)
            }
        }
        previewBody = TextView(this).apply { setText(R.string.skin_body_preview); textSize = 15f }
        previewContent.addView(previewBody)
        preview.addView(previewContent)
        content.addView(preview, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8.dp; bottomMargin = 16.dp })
        colorFields = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(colorFields)
        content.addView(button(R.string.skin_apply) {
            runCatching {
                SkinPreferences(this).apply(draft())
                toast(getString(R.string.skin_applied))
                finish()
            }.onFailure(::showError)
        }, LinearLayout.LayoutParams(-1, -2))
        content.addView(button(R.string.skin_disable) {
            SkinPreferences(this).disable()
            toast(getString(R.string.skin_disabled))
            finish()
        }, LinearLayout.LayoutParams(-1, -2))

        loadDraft(SkinPreferences(this).saved())
        if (savedInstanceState != null) {
            nameInput.setText(savedInstanceState.getString("name", nameInput.text.toString()))
            savedInstanceState.getStringArrayList("light")?.takeIf { it.size == 13 }?.let { light = it.toMutableList() }
            savedInstanceState.getStringArrayList("dark")?.takeIf { it.size == 13 }?.let { dark = it.toMutableList() }
            editDark = savedInstanceState.getBoolean("edit_dark")
            pendingExport = savedInstanceState.getString("pending_export")
        }
        showColorFields()
        setContentView(root)
        SystemBarInsets.install(root)
    }

    private fun loadDraft(skin: Skin) {
        nameInput.setText(skin.name)
        light = skin.light.values.map(SkinColor::hex).toMutableList()
        dark = skin.dark.values.map(SkinColor::hex).toMutableList()
    }

    private fun draft(): Skin = Skin(nameInput.text.toString().trim(), SkinPalette.fromHex(light), SkinPalette.fromHex(dark))

    private fun showColorFields() {
        val values = if (editDark) dark else light
        val accent = color(androidx.appcompat.R.attr.colorPrimary)
        listOf(lightButton to !editDark, darkButton to editDark).forEach { (button, selected) ->
            button.backgroundTintList = ColorStateList.valueOf(if (selected) accent else color(R.attr.inkNoteCardBackground))
            button.setTextColor(if (selected) SkinColor.onColor(accent) else color(R.attr.inkNoteTextPrimary))
            button.strokeWidth = 1.dp
            button.strokeColor = ColorStateList.valueOf(color(R.attr.inkNoteEditorBorder))
        }
        val labels = listOf(R.string.skin_background, R.string.skin_surface, R.string.skin_text,
            R.string.skin_secondary, R.string.skin_accent, R.string.skin_outline, R.string.skin_error)
        colorFields.removeAllViews()
        colorFields.addView(TextView(this).apply {
            text = getString(if (editDark) R.string.skin_editing_dark else R.string.skin_editing_light)
            setTextColor(color(R.attr.inkNoteTextPrimary))
            textSize = 18f
        })
        values.indices.forEach { index ->
            val input = TextInputEditText(this).apply {
                setSingleLine()
                background = null
                setText(values[index])
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            }
            val field = TextInputLayout(this).apply {
                hint = if (index < labels.size) getString(labels[index]) else getString(R.string.skin_heading_label, index - 6)
                boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                endIconMode = TextInputLayout.END_ICON_CUSTOM
                setEndIconDrawable(R.drawable.ic_palette_24)
                setEndIconContentDescription(R.string.skin_pick_color)
                setEndIconTintList(ColorStateList.valueOf(color(R.attr.inkNoteIconColor)))
                setEndIconOnClickListener { chooseColor(input) }
                addView(input, LinearLayout.LayoutParams(-1, -2))
            }
            input.doAfterTextChanged { text ->
                values[index] = text.toString().trim()
                val valid = runCatching { SkinColor.parse(values[index]) }.getOrNull()
                field.error = if (valid == null) getString(R.string.skin_invalid_color) else null
                valid?.let { showSwatch(field, it) }
                updatePreview()
            }
            val initial = runCatching { SkinColor.parse(values[index]) }.getOrNull()
            field.error = if (initial == null) getString(R.string.skin_invalid_color) else null
            initial?.let { showSwatch(field, it) }
            colorFields.addView(field, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 10.dp })
        }
        updatePreview()
    }

    private fun updatePreview() {
        val palette = runCatching { SkinPalette.fromHex(if (editDark) dark else light) }.getOrNull() ?: return
        preview.setCardBackgroundColor(palette.background)
        preview.setStrokeColor(palette.outline)
        previewHeadings.forEachIndexed { index, view -> view.setTextColor(palette.heading(index + 1)) }
        previewBody.setTextColor(palette.text)
    }

    private fun showSwatch(field: TextInputLayout, value: Int) {
        field.startIconDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setSize(20.dp, 20.dp)
            setColor(value)
            setStroke(1.dp, color(R.attr.inkNoteEditorBorder))
        }
        field.setStartIconTintList(null)
        field.setStartIconCheckable(false)
        field.startIconContentDescription = SkinColor.hex(value)
    }

    private fun chooseColor(input: TextInputEditText) {
        val colors = (Skin.Default.light.values + Skin.Default.dark.values).distinct()
        val grid = GridLayout(this).apply { columnCount = 4; setPadding(16.dp, 12.dp, 16.dp, 12.dp) }
        val dialog = MaterialAlertDialogBuilder(this).setTitle(R.string.skin_pick_color)
            .setView(ScrollView(this).apply { addView(grid) })
            .setNegativeButton(android.R.string.cancel, null).create()
        colors.forEach { value ->
            grid.addView(TextView(this).apply {
                text = SkinColor.hex(value)
                textSize = 10f
                gravity = Gravity.CENTER
                contentDescription = SkinColor.hex(value)
                setTextColor(SkinColor.onColor(value))
                background = GradientDrawable().apply {
                    cornerRadius = 8.dp.toFloat()
                    setColor(value)
                    setStroke(1.dp, color(R.attr.inkNoteEditorBorder))
                }
                setOnClickListener { input.setText(SkinColor.hex(value)); dialog.dismiss() }
            }, GridLayout.LayoutParams().apply {
                width = 0; height = 48.dp
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(4.dp, 4.dp, 4.dp, 4.dp)
            })
        }
        dialog.show()
    }

    private fun button(label: Int, action: () -> Unit) = MaterialButton(this).apply {
        setText(label)
        setOnClickListener { action() }
    }
    private fun color(attribute: Int) = ThemeColors.resolve(this, attribute)
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    private fun showError(error: Throwable) = toast(getString(R.string.skin_failed, error.message ?: getString(R.string.unknown_error)))
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("name", nameInput.text.toString())
        outState.putStringArrayList("light", ArrayList(light))
        outState.putStringArrayList("dark", ArrayList(dark))
        outState.putBoolean("edit_dark", editDark)
        outState.putString("pending_export", pendingExport)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }
}
