package com.mlevngr.inknote.markdown

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Reads a standalone Markdown document before any note is created. */
object MarkdownNoteImport {
    private const val MAX_BYTES = 10 * 1024 * 1024

    fun noteName(displayName: String): String {
        val extension = displayName.substringAfterLast('.', "")
        require(extension.equals("md", true) || extension.equals("markdown", true)) {
            "请选择 .md 或 .markdown 文件"
        }
        return displayName.substringBeforeLast('.').ifBlank { "导入笔记" }
    }

    fun read(input: InputStream): String {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            require(output.size() + count <= MAX_BYTES) { "Markdown 文件不能超过 10 MB" }
            output.write(buffer, 0, count)
        }
        val bytes = output.toByteArray()
        val (charset, offset) = when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() &&
                bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> Charsets.UTF_8 to 3
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                Charsets.UTF_16LE to 2
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                Charsets.UTF_16BE to 2
            else -> Charsets.UTF_8 to 0
        }
        val markdown = try {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset))
                .toString()
        } catch (error: java.nio.charset.CharacterCodingException) {
            throw IllegalArgumentException("文件编码无法识别，请使用 UTF-8 或带 BOM 的 UTF-16", error)
        }
        require('\u0000' !in markdown) { "所选文件不是 Markdown 文本" }
        return markdown
    }
}
