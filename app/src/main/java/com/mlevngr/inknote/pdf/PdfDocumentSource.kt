package com.mlevngr.inknote.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File

class PdfDocumentSource(file: File) : Closeable {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)

    val pageCount: Int get() = renderer.pageCount

    @Synchronized
    fun render(pageIndex: Int, targetWidth: Int): Bitmap {
        renderer.openPage(pageIndex).use { page ->
            val width = targetWidth.coerceAtLeast(320)
            val height = (width.toFloat() * page.height / page.width)
                .toInt()
                .coerceIn(320, 4096)
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
}
