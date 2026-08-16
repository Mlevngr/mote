package com.mlevngr.inknote.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File
import kotlin.math.sqrt

class PdfDocumentSource(file: File) : Closeable {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)

    val pageCount: Int get() = renderer.pageCount

    @Synchronized
    fun render(pageIndex: Int, targetWidth: Int): Bitmap {
        renderer.openPage(pageIndex).use { page ->
            val pageRatio = page.height.toFloat() / page.width
            val requestedWidth = targetWidth.coerceAtLeast(320)
            val requestedHeight = requestedWidth * pageRatio
            val pixelScale = minOf(
                1f,
                MAX_BITMAP_EDGE / requestedWidth,
                MAX_BITMAP_EDGE / requestedHeight,
                sqrt(MAX_BITMAP_PIXELS / (requestedWidth * requestedHeight))
            )
            val width = (requestedWidth * pixelScale).toInt().coerceAtLeast(320)
            val height = (width * pageRatio).toInt().coerceAtLeast(320)
            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }
    }

    @Synchronized
    override fun close() {
        renderer.close()
        descriptor.close()
    }

    private companion object {
        const val MAX_BITMAP_EDGE = 6144f
        const val MAX_BITMAP_PIXELS = 12_000_000f
    }
}
