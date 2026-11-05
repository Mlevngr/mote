package com.mlevngr.inknote.appearance

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SkinColorTableTest {
    @Test fun producesACompleteSparseColorTableWithCorrectArgbValues() {
        val colors = mapOf(0x7F060002 to 0xFF102030.toInt(), 0x7F060005 to 0xFFAABBCC.toInt())
        val bytes = SkinColorTable.create("com.mlevngr.inknote", colors)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x0002, buffer.getShort(0).toInt())
        assertEquals(12, buffer.getShort(2).toInt())
        assertEquals(bytes.size, buffer.getInt(4))
        assertEquals(1, buffer.getInt(8))
        val pkg = 12 + buffer.getInt(16)
        assertEquals(0x0200, buffer.getShort(pkg).toInt())
        assertEquals(0x7F, buffer.getInt(pkg + 8))
        var cursor = pkg + 288
        repeat(2) { cursor += buffer.getInt(cursor + 4) }
        assertEquals(0x0202, buffer.getShort(cursor).toInt())
        cursor += buffer.getInt(cursor + 4)
        assertEquals(0x0201, buffer.getShort(cursor).toInt())
        assertEquals(6, buffer.get(cursor + 8).toInt())
        assertEquals(6, buffer.getInt(cursor + 12))
        val entries = cursor + buffer.getInt(cursor + 16)
        assertEquals(-1, buffer.getInt(cursor + 84))
        assertEquals(0, buffer.getInt(cursor + 84 + 2 * 4))
        assertEquals(16, buffer.getInt(cursor + 84 + 5 * 4))
        assertEquals(0x1C, buffer.get(entries + 11).toInt())
        assertEquals(colors.getValue(0x7F060002), buffer.getInt(entries + 12))
        assertEquals(colors.getValue(0x7F060005), buffer.getInt(entries + 28))
        // Also consumed by the local aapt2 structural verification step.
        File("build/skin-verification").apply { mkdirs() }.resolve("resources.arsc").writeBytes(bytes)
    }

    @Test fun refusesEmptyOrMixedPackageAndTypeIds() {
        listOf(emptyMap(), mapOf(0x01060000 to -1), mapOf(0x7F060000 to -1, 0x7F070001 to -1)).forEach {
            colors -> assertThrows(IllegalArgumentException::class.java) { SkinColorTable.create("test", colors) }
        }
    }
}
