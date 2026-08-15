package com.mlevngr.inknote.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import com.mlevngr.inknote.assets.AssetPathPolicy
import com.mlevngr.inknote.library.NoteLibrary
import com.mlevngr.inknote.markdown.MarkdownAssetParser
import com.mlevngr.inknote.markdown.PreviewBlock
import com.mlevngr.inknote.pdf.PdfDocumentSource
import java.io.File
import kotlin.math.roundToInt

/** Draws a compact, clipped representation of the note's first rendered page. */
class NotePageThumbnailRenderer(
    private val pageColor: Int,
    private val textColor: Int,
    private val secondaryTextColor: Int,
    private val accentColor: Int
) {
    fun render(entry: NoteLibrary.Entry, width: Int, height: Int): Bitmap {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(pageColor)

        val padding = (width * 0.055f).roundToInt().coerceAtLeast(8)
        val contentWidth = width - padding * 2
        val bottom = height - padding
        var y = padding

        y = drawText(
            canvas = canvas,
            text = entry.name,
            x = padding,
            y = y,
            width = contentWidth,
            bottom = bottom,
            size = width * 0.078f,
            color = textColor,
            typeface = Typeface.DEFAULT_BOLD,
            spacing = width * 0.022f
        )

        val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            alpha = 90
            strokeWidth = (width * 0.006f).coerceAtLeast(2f)
        }
        canvas.drawLine(
            padding.toFloat(),
            y.toFloat(),
            (padding + contentWidth * 0.28f),
            y.toFloat(),
            divider
        )
        y += (width * 0.035f).roundToInt()

        val blocks = MarkdownAssetParser.parse(entry.preview.renderSource)
        for (block in blocks) {
            if (y >= bottom) break
            y = when (block) {
                is PreviewBlock.Markdown -> drawMarkdown(
                    canvas, block.source, padding, y, contentWidth, bottom, width
                )
                is PreviewBlock.Image -> drawAsset(
                    canvas,
                    resolve(entry.noteDirectory, block.relativePath),
                    padding,
                    y,
                    contentWidth,
                    bottom,
                    isPdf = false
                )
                is PreviewBlock.Pdf -> drawAsset(
                    canvas,
                    resolve(entry.noteDirectory, block.relativePath),
                    padding,
                    y,
                    contentWidth,
                    bottom,
                    isPdf = true
                )
                is PreviewBlock.Attachment -> drawText(
                    canvas,
                    "▣  ${block.label.ifBlank { block.relativePath.substringAfterLast('/') }}",
                    padding,
                    y,
                    contentWidth,
                    bottom,
                    width * 0.038f,
                    secondaryTextColor,
                    Typeface.DEFAULT,
                    width * 0.018f
                )
            }
        }

        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = secondaryTextColor
            alpha = 40
            strokeWidth = 1f
        }
        canvas.drawRect(0.5f, 0.5f, width - 0.5f, height - 0.5f, border)
        return bitmap
    }

    private fun drawMarkdown(
        canvas: Canvas,
        source: String,
        x: Int,
        startY: Int,
        width: Int,
        bottom: Int,
        pageWidth: Int
    ): Int {
        var y = startY
        var inCodeBlock = false
        source.lineSequence().forEach { raw ->
            if (y >= bottom) return@forEach
            val trimmed = raw.trim()
            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock
                return@forEach
            }
            if (trimmed.isEmpty()) {
                y += (pageWidth * 0.018f).roundToInt()
                return@forEach
            }

            val heading = Regex("^(#{1,6})\\s+(.*)$").matchEntire(trimmed)
            val size: Float
            val typeface: Typeface
            val color: Int
            val text = when {
                heading != null -> {
                    val level = heading.groupValues[1].length
                    size = pageWidth * (0.061f - level * 0.0035f)
                    typeface = Typeface.DEFAULT_BOLD
                    color = textColor
                    cleanInline(heading.groupValues[2])
                }
                inCodeBlock || raw.startsWith("    ") -> {
                    size = pageWidth * 0.034f
                    typeface = Typeface.MONOSPACE
                    color = secondaryTextColor
                    raw.trimStart()
                }
                trimmed.startsWith(">") -> {
                    size = pageWidth * 0.038f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                    color = secondaryTextColor
                    "│  ${cleanInline(trimmed.removePrefix(">").trimStart())}"
                }
                Regex("^[-*] \\[[xX ]]\\s+.*").matches(trimmed) -> {
                    size = pageWidth * 0.039f
                    typeface = Typeface.DEFAULT
                    color = textColor
                    val checked = trimmed.substringAfter('[').firstOrNull()?.equals('x', true) == true
                    "${if (checked) "☑" else "☐"}  ${cleanInline(trimmed.substringAfter(']').trimStart())}"
                }
                Regex("^[-+*]\\s+.*").matches(trimmed) -> {
                    size = pageWidth * 0.039f
                    typeface = Typeface.DEFAULT
                    color = textColor
                    "•  ${cleanInline(trimmed.drop(2))}"
                }
                Regex("^\\d+[.)]\\s+.*").matches(trimmed) -> {
                    size = pageWidth * 0.039f
                    typeface = Typeface.DEFAULT
                    color = textColor
                    cleanInline(trimmed)
                }
                else -> {
                    size = pageWidth * 0.039f
                    typeface = Typeface.DEFAULT
                    color = textColor
                    cleanInline(trimmed)
                }
            }
            if (text.isNotEmpty()) {
                y = drawText(
                    canvas, text, x, y, width, bottom, size, color, typeface, pageWidth * 0.014f
                )
            }
        }
        return y
    }

    private fun drawText(
        canvas: Canvas,
        text: String,
        x: Int,
        y: Int,
        width: Int,
        bottom: Int,
        size: Float,
        color: Int,
        typeface: Typeface,
        spacing: Float
    ): Int {
        if (text.isBlank() || y >= bottom) return y
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size.coerceAtLeast(8f)
            this.color = color
            this.typeface = typeface
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(0f, 1.08f)
            .build()
        canvas.withClip(x, y, x + width, bottom) {
            translate(x.toFloat(), y.toFloat())
            layout.draw(this)
        }
        return (y + layout.height + spacing).roundToInt().coerceAtMost(bottom)
    }

    private fun drawAsset(
        canvas: Canvas,
        file: File?,
        x: Int,
        y: Int,
        width: Int,
        bottom: Int,
        isPdf: Boolean
    ): Int {
        if (file == null || y >= bottom) return y
        val source = runCatching {
            if (isPdf) PdfDocumentSource(file).use { it.render(0, width) }
            else decodeSampled(file, width, bottom - y)
        }.getOrNull() ?: return y
        val availableHeight = bottom - y
        val targetHeight = (width.toFloat() * source.height / source.width)
            .roundToInt()
            .coerceAtMost(availableHeight)
        if (targetHeight <= 0) {
            source.recycle()
            return y
        }
        val target = Rect(x, y, x + width, y + targetHeight)
        canvas.drawBitmap(source, null, target, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        source.recycle()
        return (y + targetHeight + width * 0.025f).roundToInt().coerceAtMost(bottom)
    }

    private fun decodeSampled(file: File, width: Int, height: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > width * 2 || bounds.outHeight / sample > height * 2) {
            sample *= 2
        }
        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }

    private fun resolve(noteDirectory: File?, path: String): File? = noteDirectory
        ?.let { AssetPathPolicy.resolve(it, path) }
        ?.takeIf(File::isFile)

    private fun cleanInline(source: String): String = source
        .replace(Regex("!\\[([^]]*)]\\([^)]*\\)"), "$1")
        .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
        .replace(Regex("[`*_~]"), "")
        .replace('|', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
}
