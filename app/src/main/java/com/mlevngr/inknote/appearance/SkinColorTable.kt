package com.mlevngr.inknote.appearance

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal Android resources.arsc containing only this app's skin color overrides.
 * Format: AOSP ResourceTypes.h; no hidden APIs or Material internal helpers are used.
 */
object SkinColorTable {
    fun create(packageName: String, colors: Map<Int, Int>, names: Map<Int, String> = emptyMap()): ByteArray {
        require(colors.isNotEmpty())
        val ids = colors.keys.sorted()
        val typeId = (ids.first() ushr 16) and 255
        require(typeId > 0 && ids.all { it ushr 24 == 0x7F && (it ushr 16) and 255 == typeId })
        val count = (ids.last() and 0xFFFF) + 1
        val types = stringPool(List(typeId) { if (it == typeId - 1) "color" else "" })
        val keys = stringPool(ids.map { names[it] ?: "skin_${it and 0xFFFF}" })

        val spec = chunk(0x0202, 16, 16 + 4 * count).apply {
            put(typeId.toByte()).put(0).putShort(0).putInt(count)
            repeat(count) { putInt(if ((0x7F000000 or (typeId shl 16) or it) in colors) 0x40000000 else 0) }
        }.array()
        // ResTable_type: 20-byte header followed by a 64-byte default ResTable_config.
        val entriesStart = 84 + 4 * count
        val type = chunk(0x0201, 84, entriesStart + ids.size * 16).apply {
            put(typeId.toByte()).put(0).putShort(0).putInt(count).putInt(entriesStart)
            putInt(64)
            repeat(60) { put(0) }
            val indices = ids.withIndex().associate { (index, id) -> (id and 0xFFFF) to index }
            repeat(count) { putInt(indices[it]?.times(16) ?: -1) }
            ids.forEachIndexed { index, id ->
                putShort(8).putShort(2).putInt(index) // ResTable_entry, public
                putShort(8).put(0).put(0x1C).putInt(colors.getValue(id)) // TYPE_INT_COLOR_ARGB8
            }
        }.array()
        val packageSize = 288 + types.size + keys.size + spec.size + type.size
        val pkg = chunk(0x0200, 288, packageSize).apply {
            putInt(0x7F)
            require(packageName.length < 128)
            repeat(128) { putShort((packageName.getOrNull(it)?.code ?: 0).toShort()) }
            putInt(288).putInt(0).putInt(288 + types.size).putInt(0).putInt(0)
            put(types).put(keys).put(spec).put(type)
        }.array()
        val globalStrings = stringPool(emptyList())
        return chunk(0x0002, 12, 12 + globalStrings.size + pkg.size)
            .putInt(1).put(globalStrings).put(pkg).array()
    }

    private fun stringPool(strings: List<String>): ByteArray {
        val encoded = strings.map { value ->
            val bytes = value.toByteArray(Charsets.UTF_8)
            require(value.length < 128 && bytes.size < 128)
            byteArrayOf(value.length.toByte(), bytes.size.toByte()) + bytes + byteArrayOf(0)
        }
        val start = 28 + strings.size * 4
        val rawSize = start + encoded.sumOf { it.size }
        val size = (rawSize + 3) and -4
        return chunk(0x0001, 28, size).apply {
            putInt(strings.size).putInt(0).putInt(0x100).putInt(start).putInt(0)
            var offset = 0
            encoded.forEach { putInt(offset); offset += it.size }
            encoded.forEach { put(it) }
        }.array()
    }

    private fun chunk(type: Int, header: Int, size: Int): ByteBuffer =
        ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(type.toShort()).putShort(header.toShort()).putInt(size)
}
