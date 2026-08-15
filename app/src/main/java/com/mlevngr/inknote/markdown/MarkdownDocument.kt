package com.mlevngr.inknote.markdown

/** A small block model for hybrid Markdown editing. Blocks are separated by blank lines. */
class MarkdownDocument private constructor(private val blocks: MutableList<String>) {
    val size: Int get() = blocks.size

    operator fun get(index: Int): String = blocks[index]

    fun snapshot(): List<String> = blocks.toList()

    fun update(index: Int, source: String) {
        blocks[index] = source
    }

    fun insertAfter(index: Int?, source: String): Int {
        val position = if (index == null) blocks.size else (index + 1).coerceAtMost(blocks.size)
        blocks.add(position, source)
        return position
    }

    fun markdown(): String = blocks.joinToString("\n\n")

    companion object {
        fun parse(source: String): MarkdownDocument {
            if (source.isBlank()) return MarkdownDocument(mutableListOf(""))
            val result = mutableListOf<String>()
            val current = StringBuilder()
            var insideFence = false

            fun flush() {
                val block = current.toString().trimEnd('\n')
                if (block.isNotBlank()) result += block
                current.clear()
            }

            source.lineSequence().forEach { line ->
                val isFence = line.trimStart().startsWith("```") ||
                        line.trimStart().startsWith("~~~")
                if (line.isBlank() && !insideFence) {
                    flush()
                } else {
                    if (current.isNotEmpty()) current.append('\n')
                    current.append(line)
                    if (isFence) insideFence = !insideFence
                }
            }
            flush()
            if (result.isEmpty()) result += ""
            return MarkdownDocument(result)
        }
    }
}
