package com.mlevngr.inknote

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.textfield.TextInputEditText
import com.mlevngr.inknote.appearance.*
import com.mlevngr.inknote.library.NoteLibrary
import com.mlevngr.inknote.library.NotePreview
import com.mlevngr.inknote.ui.NotePageThumbnailRenderer
import io.noties.markwon.Markwon
import io.noties.markwon.core.spans.HeadingSpan
import java.io.File

/** Run only against a disposable emulator; uses real Activities and document-result callbacks. */
open class SkinSmokeInstrumentation : Instrumentation() {
    private val activities = mutableListOf<Activity>()
    private val fixture = Skin.Default.copy(name = "本地皮肤验证", light = Skin.Default.light.copy(
        background = SkinColor.parse("#FFF8EC"), text = SkinColor.parse("#18384A"), accent = SkinColor.parse("#265A44")
    ))

    protected fun runSkinSmokeTests() {
        val tests = listOf<Pair<String, () -> Unit>>(
            "runtimePaletteAndBuiltinIsolation" to ::runtimePalette,
            "sixHeadingColorsAndThumbnail" to ::headingColors,
            "headingsAreColoredInsideTheActualEditor" to ::editorHeadingColors,
            "documentImportExportAndInvalidInput" to ::documents,
            "editablePaletteIsApplied" to ::editPalette
        )
        targetContext.getSharedPreferences("navigation_ui", 0).edit()
            .putBoolean("drawer_introduction_build_44", true)
            .putBoolean("shared_storage_introduction_build_50", true).commit()
        var failures = 0
        tests.forEachIndexed { index, (name, test) ->
            val status = Bundle().apply {
                putString("class", javaClass.name); putString("test", name)
                putInt("numtests", tests.size); putInt("current", index + 1)
            }
            sendStatus(1, status)
            try {
                test()
                sendStatus(0, status)
            } catch (error: Throwable) {
                failures++
                status.putString("stack", error.stackTraceToString())
                sendStatus(-2, status)
            } finally {
                runOnMainSync { activities.forEach { it.finish() }; activities.clear() }
                SkinPreferences(targetContext).disable()
                waitForIdleSync()
            }
        }
        finish(if (failures == 0) Activity.RESULT_OK else Activity.RESULT_CANCELED, Bundle().apply {
            putString("stream", "\nSkin smoke: ${tests.size - failures}/${tests.size} passed\n")
            putInt("failures", failures)
        })
    }

    private fun runtimePalette() {
        SkinPreferences(targetContext).apply(fixture)
        val main = open(MainActivity::class.java)
        check(ThemeColors.resolve(main, R.attr.inkNoteAppBackground) == fixture.light.background)
        check(main.findViewById<TextView>(R.id.library_title).currentTextColor == fixture.light.text)
        check(ThemeColors.resolve(main, androidx.appcompat.R.attr.colorPrimary) == fixture.light.accent)
        val night = targetContext.createConfigurationContext(Configuration(targetContext.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        })
        val wrapped = SkinTheme.wrap(night)
        check(SkinTheme.isApplied(wrapped))
        check(ThemeColors.resolve(wrapped, R.attr.inkNoteAppBackground) == fixture.dark.background)
        check(ThemeColors.resolve(wrapped, androidx.appcompat.R.attr.colorPrimary) == fixture.dark.accent)
        check(targetContext.getColor(R.color.mote_skin_accent) == SkinColor.parse("#1A73E8")) { "Skin leaked into base resources" }
        runOnMainSync { main.finish() }
        SkinPreferences(targetContext).disable()
        val restored = open(MainActivity::class.java)
        check(ThemeColors.resolve(restored, R.attr.inkNoteTextPrimary) == SkinColor.parse("#202124"))
    }

    private fun headingColors() {
        val markdown = (1..6).joinToString("\n\n") { "${"#".repeat(it)} Heading $it" }
        val markwon = Markwon.builder(targetContext).usePlugin(SkinHeadingPlugin(fixture.light)).build()
        val rendered = markwon.toMarkdown(markdown + "\n\nSetext\n===\n\n```\n# code\n```\n\n    # indented code")
        val colors = rendered.getSpans(0, rendered.length, ForegroundColorSpan::class.java)
            .sortedBy { rendered.getSpanStart(it) }.map { it.foregroundColor }
        check(colors == fixture.light.headings + fixture.light.heading(1)) { "Heading colors differ: $colors" }
        check(rendered.getSpans(0, rendered.length, HeadingSpan::class.java).size == 7) { "Original heading spans were replaced" }
        val entry = NoteLibrary.Entry("Heading sample", "sample.note", NoteLibrary.EntryType.Note, 0,
            preview = NotePreview("", markdown))
        val palette = fixture.light
        val bitmap = NotePageThumbnailRenderer(palette.background, palette.text, palette.secondaryText,
            palette.accent, palette.headings).render(entry, 400, 800)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        palette.headings.forEach { check(it in pixels) { "Thumbnail is missing heading color ${SkinColor.hex(it)}" } }
        bitmap.recycle()
    }

    private fun editorHeadingColors() {
        SkinPreferences(targetContext).apply(fixture)
        val library = NoteLibrary(targetContext)
        val markdown = (1..6).joinToString("\n") { "${"#".repeat(it)} Editor heading $it" }
        val note = library.createNote(NoteLibrary.FolderLocation.Root, "Skin editor regression", markdown)
        val editor = startActivitySync(EditorActivity.intent(targetContext, NoteLibrary.FolderLocation.Root, note.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as EditorActivity
        activities += editor
        waitForIdleSync()
        await { views(editor.window.decorView).filterIsInstance<TextView>().any { it.text.toString() == "Editor heading 6" } }
        try {
            runOnMainSync {
                (1..6).forEach { level ->
                    val view = views(editor.window.decorView).filterIsInstance<TextView>()
                        .first { it.text.toString() == "Editor heading $level" }
                    val text = view.text as android.text.Spanned
                    val spans = text.getSpans(0, text.length, ForegroundColorSpan::class.java)
                    val expected = fixture.light.heading(level)
                    check(spans.any { it.foregroundColor == expected }) {
                        "Editor H$level has no expected color span; spans=${spans.map { SkinColor.hex(it.foregroundColor) }}"
                    }
                    val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
                    view.draw(android.graphics.Canvas(bitmap))
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    check(expected in pixels) { "Editor H$level renders without ${SkinColor.hex(expected)}" }
                    bitmap.recycle()
                }
            }
        } finally {
            val screenshot = uiAutomation.takeScreenshot()
            File(targetContext.filesDir, "skin-editor.png").outputStream().use {
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            screenshot.recycle()
        }
    }

    private fun documents() {
        SkinPreferences(targetContext).apply(fixture)
        val settings = open(SkinSettingsActivity::class.java)
        val imported = fixture.copy(name = "导入的皮肤", light = fixture.light.copy(accent = SkinColor.parse("#AA3377")))
        val baseUri = Uri.parse("content://com.mlevngr.inknote.test.skin")
        targetContext.contentResolver.call(baseUri, "seed", SkinJson.encode(imported), null)
        pickDocument(settings, Intent.ACTION_OPEN_DOCUMENT, Uri.withAppendedPath(baseUri, "valid"), R.string.skin_import)
        await { inputs(settings).first().text.toString() == imported.name }
        check(SkinPreferences(targetContext).active() == fixture) { "Import applied without confirmation" }
        pickDocument(settings, Intent.ACTION_CREATE_DOCUMENT, Uri.withAppendedPath(baseUri, "export"), R.string.skin_export)
        await {
            runCatching {
                val stream = requireNotNull(targetContext.contentResolver.openInputStream(Uri.withAppendedPath(baseUri, "export")))
                stream.use(SkinJson::read) == imported
            }.getOrDefault(false)
        }
        pickDocument(settings, Intent.ACTION_OPEN_DOCUMENT, Uri.withAppendedPath(baseUri, "invalid"), R.string.skin_import)
        SystemClock.sleep(400)
        check(inputs(settings).first().text.toString() == imported.name)
        check(SkinPreferences(targetContext).active() == fixture)
        val bitmap = uiAutomation.takeScreenshot()
        File(targetContext.filesDir, "skin-settings.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun editPalette() {
        SkinPreferences(targetContext).disable()
        val settings = open(SkinSettingsActivity::class.java)
        runOnMainSync {
            val picker = views(settings.window.decorView).first {
                it.contentDescription?.toString() == settings.getString(R.string.skin_pick_color)
            }
            check(picker.performClick())
        }
        waitForIdleSync()
        val preset = uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("#7C3AED").first()
        check(preset.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
        await { inputs(settings)[1].text.toString() == "#7C3AED" }
        runOnMainSync {
            click(settings, R.string.skin_dark)
            inputs(settings)[8].setText("#99CCEE")
            click(settings, R.string.skin_light)
            check(inputs(settings)[1].text.toString() == "#7C3AED") { "Switching modes discarded the light draft" }
        }
        runOnMainSync {
            val fields = inputs(settings)
            fields[0].setText("编辑后的皮肤")
            fields[1].setText("#F9F3E8")
            fields[8].setText("#883377")
            click(settings, R.string.skin_apply)
        }
        val saved = requireNotNull(SkinPreferences(targetContext).active())
        check(saved.name == "编辑后的皮肤")
        check(saved.light.background == SkinColor.parse("#F9F3E8"))
        check(saved.light.heading(1) == SkinColor.parse("#883377"))
        check(saved.dark.heading(1) == SkinColor.parse("#99CCEE"))
        val main = open(MainActivity::class.java)
        check(ThemeColors.resolve(main, R.attr.inkNoteAppBackground) == saved.light.background)
    }

    private fun <T : Activity> open(type: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        val activity = startActivitySync(Intent(targetContext, type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as T
        activities += activity
        waitForIdleSync()
        return activity
    }

    private fun pickDocument(activity: Activity, action: String, uri: Uri, label: Int) {
        val filter = IntentFilter(action).apply { addDataType("*/*"); addCategory(Intent.CATEGORY_OPENABLE); addCategory(Intent.CATEGORY_DEFAULT) }
        val monitor = addMonitor(filter, ActivityResult(Activity.RESULT_OK, Intent().setData(uri)), true)
        try { runOnMainSync { click(activity, label) }; waitForIdleSync() } finally { removeMonitor(monitor) }
    }
    private fun views(root: View): List<View> = listOf(root) +
        if (root is ViewGroup) (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
    private fun inputs(activity: Activity) = views(activity.window.decorView).filterIsInstance<TextInputEditText>()
    private fun click(activity: Activity, label: Int) {
        val text = activity.getString(label)
        val view = views(activity.window.decorView).filterIsInstance<TextView>().first { it.text.toString() == text }
        check(view.performClick()) { "Cannot click $text" }
    }
    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 6000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < deadline) { "Timed out waiting for document result" }
            SystemClock.sleep(50)
        }
    }
}
