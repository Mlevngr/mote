package com.mlevngr.inknote.appearance

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** A bounded JSON codec for the skin schema: objects, arrays, strings and integer versions. */
object SkinJson {
    const val MAX_BYTES = 32 * 1024

    fun read(input: InputStream): Skin {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val size = input.read(buffer)
            if (size < 0) break
            require(output.size() + size <= MAX_BYTES) { "皮肤包不能超过 32 KB" }
            output.write(buffer, 0, size)
        }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(output.toByteArray())).toString()
        return decode(text.removePrefix("\uFEFF"))
    }

    fun decode(text: String): Skin {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "皮肤包不能超过 32 KB" }
        val root = objectValue(Parser(text).parse())
        require(root.keys == setOf("format", "version", "name", "light", "dark")) { "皮肤包字段不完整或不受支持" }
        require(root["format"] == "mote-skin" && root["version"] == 1) { "不支持的皮肤格式或版本" }
        return Skin(stringValue(root["name"]), palette(root["light"]), palette(root["dark"]))
    }

    fun encode(skin: Skin): String {
        fun palette(value: SkinPalette): String = buildString {
            append("{\n")
            SkinPalette.keys.forEachIndexed { index, key ->
                append("    \"$key\": \"${SkinColor.hex(value.values[index])}\",\n")
            }
            append("    \"headings\": [")
            append(value.headings.joinToString(", ") { "\"${SkinColor.hex(it)}\"" })
            append("]\n  }")
        }
        return "{\n  \"format\": \"mote-skin\",\n  \"version\": 1,\n  \"name\": ${quote(skin.name)},\n" +
            "  \"light\": ${palette(skin.light)},\n  \"dark\": ${palette(skin.dark)}\n}\n"
    }

    private fun palette(value: Any?): SkinPalette {
        val fields = objectValue(value)
        require(fields.keys == (SkinPalette.keys + "headings").toSet()) { "配色字段不完整或不受支持" }
        val headings = fields["headings"] as? List<*> ?: error("缺少 H1–H6 标题颜色")
        return SkinPalette.fromHex(SkinPalette.keys.map { stringValue(fields[it]) } + headings.map(::stringValue))
    }

    private fun objectValue(value: Any?): Map<*, *> = value as? Map<*, *> ?: error("需要 JSON 对象")
    private fun stringValue(value: Any?): String = value as? String ?: error("需要文本字段")
    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { c ->
            when (c) {
                '"', '\\' -> append('\\').append(c)
                in '\u0000'..'\u001F' -> append("\\u%04x".format(java.util.Locale.ROOT, c.code))
                else -> append(c)
            }
        }
        append('"')
    }

    private class Parser(private val source: String) {
        private var position = 0
        fun parse(): Any {
            val value = value(0)
            whitespace()
            require(position == source.length) { "JSON 末尾存在多余内容" }
            return value
        }

        private fun value(depth: Int): Any {
            require(depth <= 4) { "JSON 嵌套层数过多" }
            whitespace()
            return when (source.getOrNull(position)) {
                '{' -> {
                    position++
                    val fields = linkedMapOf<String, Any>()
                    if (!take('}')) do {
                        whitespace()
                        val key = string()
                        require(!fields.containsKey(key)) { "JSON 字段重复：$key" }
                        expect(':')
                        fields[key] = value(depth + 1)
                    } while (take(',')) else return fields
                    expect('}')
                    fields
                }
                '[' -> {
                    position++
                    val items = mutableListOf<Any>()
                    if (!take(']')) do { items += value(depth + 1) } while (take(',')) else return items
                    expect(']')
                    items
                }
                '"' -> string()
                in '0'..'9' -> {
                    val start = position
                    while (source.getOrNull(position) in '0'..'9') position++
                    val number = source.substring(start, position)
                    require(number == "0" || !number.startsWith('0')) { "无效的 JSON 数字" }
                    number.toInt()
                }
                else -> error("无效的 JSON 值，位置 $position")
            }
        }

        private fun string(): String {
            expect('"')
            return buildString {
                while (position < source.length) {
                    val c = source[position++]
                    if (c == '"') return@buildString
                    require(c >= ' ') { "JSON 文本含未转义的控制字符" }
                    if (c != '\\') append(c) else {
                        require(position < source.length) { "JSON 转义不完整" }
                        append(when (val escaped = source[position++]) {
                            '"', '\\', '/' -> escaped
                            'b' -> '\b'; 'f' -> '\u000C'; 'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'
                            'u' -> {
                                require(position + 4 <= source.length) { "Unicode 转义不完整" }
                                source.substring(position, position + 4).toInt(16).toChar().also { position += 4 }
                            }
                            else -> error("无效的 JSON 转义")
                        })
                    }
                }
                error("JSON 文本缺少结束引号")
            }
        }

        private fun whitespace() { while (source.getOrNull(position) in listOf(' ', '\t', '\n', '\r')) position++ }
        private fun take(c: Char): Boolean {
            whitespace()
            return (source.getOrNull(position) == c).also { if (it) position++ }
        }
        private fun expect(c: Char) { require(take(c)) { "JSON 缺少 $c，位置 $position" } }
    }
}
