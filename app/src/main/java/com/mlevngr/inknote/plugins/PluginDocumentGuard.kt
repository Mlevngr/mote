package com.mlevngr.inknote.plugins

import com.mlevngr.inknote.markdown.PdfPageNotes
import java.security.MessageDigest

object PluginDocumentGuard {
    private const val MAX_RESULT_CHARS = 2_000_000

    fun revision(markdown: String): String = MessageDigest.getInstance("SHA-256")
        .digest(markdown.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun validate(original: String, proposed: String): ValidationResult {
        if (proposed.isBlank()) return ValidationResult.Invalid("插件返回了空笔记")
        if (proposed.length > MAX_RESULT_CHARS) {
            return ValidationResult.Invalid("插件结果超过安全大小限制")
        }
        if (protectedLines(original) != protectedLines(proposed)) {
            return ValidationResult.Invalid("插件修改或删除了图片、PDF、附件或页间笔记标记")
        }
        return ValidationResult.Valid
    }

    private fun protectedLines(markdown: String): List<String> = markdown.lineSequence()
        .filter { line -> "assets/" in line || PdfPageNotes.isMarker(line) }
        .toList()

    sealed interface ValidationResult {
        data object Valid : ValidationResult
        data class Invalid(val reason: String) : ValidationResult
    }
}
