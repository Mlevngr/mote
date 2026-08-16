package com.mlevngr.mote.plugin.local

internal object LocalMarkdownOrganizer {
    private val heading = Regex("^(#{1,6})\\s*(.+?)\\s*$")
    private val headingLabel = Regex("^([^#\\[\\]{}]{1,24})[：:]\\s*$")
    private val keyValue = Regex("^([^#：:]{1,16})[：:]\\s+(.+)$")
    private val bullet = Regex("^(\\s*)[•·*+]\\s+(.+)$")
    private val looseTask = Regex("^(\\s*)-?\\s*\\[\\s*]\\s*(.+)$")
    private val todo = Regex("^(\\s*)(?:TODO|FIXME|待办|任务)\\s*[：:]\\s*(.+)$", RegexOption.IGNORE_CASE)
    private val ordered = Regex("^(\\s*)\\d+(?:[.)]\\s+|、\\s*)(.+)$")
    private val existingTask = Regex("^\\s*-\\s*\\[[ xX]]\\s+(.+)$")
    private val likelyAction = Regex(
        "^(?:需要|应该|必须|记得|计划|请|完成|联系|购买|提交|处理|检查|确认|更新|修复|TODO|FIXME)[：:、，,\\s]*(.+)$",
        RegexOption.IGNORE_CASE
    )

    fun cleanup(markdown: String): String = transform(markdown, structure = false)

    fun organize(markdown: String): String = transform(markdown, structure = true)

    fun extractTasks(markdown: String): String {
        val organized = organize(markdown)
        if (organized.lineSequence().any { it.trim() == "## 行动项" }) return organized
        val tasks = LinkedHashSet<String>()
        organized.lineSequence().forEach { line ->
            existingTask.matchEntire(line)?.groupValues?.get(1)?.cleanTask()?.let(tasks::add)
            if (!isProtected(line)) {
                likelyAction.matchEntire(line.trim().removeMarkdownPrefix())
                    ?.groupValues?.get(1)?.cleanTask()?.let(tasks::add)
            }
        }
        if (tasks.isEmpty()) return organized
        return buildString {
            appendLine("## 行动项")
            tasks.forEach { appendLine("- [ ] $it") }
            appendLine()
            append(organized)
        }.trimEnd()
    }

    private fun transform(markdown: String, structure: Boolean): String {
        val result = mutableListOf<String>()
        var inFence = false
        var fenceMarker: String? = null
        var orderedIndent: String? = null
        var orderedIndex = 0

        markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { original ->
            val trimmed = original.trimStart()
            val marker = when {
                trimmed.startsWith("```") -> "```"
                trimmed.startsWith("~~~") -> "~~~"
                else -> null
            }
            if (marker != null) {
                result += original
                if (!inFence) {
                    inFence = true
                    fenceMarker = marker
                } else if (marker == fenceMarker) {
                    inFence = false
                    fenceMarker = null
                }
                orderedIndent = null
                return@forEach
            }
            if (inFence || isProtected(original)) {
                result += original
                orderedIndent = null
                return@forEach
            }

            val line = original.trimEnd()
            if (line.isBlank()) {
                if (result.lastOrNull()?.isNotBlank() == true) result += ""
                return@forEach
            }

            val normalized = when {
                heading.matches(line) -> heading.replace(line) { "${it.groupValues[1]} ${it.groupValues[2]}" }
                todo.matches(line) -> todo.replace(line) { "${it.groupValues[1]}- [ ] ${it.groupValues[2]}" }
                looseTask.matches(line) -> looseTask.replace(line) { "${it.groupValues[1]}- [ ] ${it.groupValues[2]}" }
                bullet.matches(line) -> bullet.replace(line) { "${it.groupValues[1]}- ${it.groupValues[2]}" }
                structure && headingLabel.matches(line) -> headingLabel.replace(line) { "## ${it.groupValues[1].trim()}" }
                structure && keyValue.matches(line) && "://" !in line -> keyValue.replace(line) {
                    "**${it.groupValues[1].trim()}：** ${it.groupValues[2].trim()}"
                }
                else -> line
            }

            val orderedMatch = ordered.matchEntire(normalized)
            if (orderedMatch != null) {
                val indent = orderedMatch.groupValues[1]
                orderedIndex = if (orderedIndent == indent) orderedIndex + 1 else 1
                orderedIndent = indent
                result += "$indent$orderedIndex. ${orderedMatch.groupValues[2]}"
            } else {
                orderedIndent = null
                result += normalized
            }
        }
        return result.dropLastWhile(String::isBlank).joinToString("\n")
    }

    private fun isProtected(line: String): Boolean =
        "assets/" in line || line.trimStart().startsWith("<!-- mote:")

    private fun String.cleanTask(): String? = trim()
        .trimEnd('.', '。', ';', '；')
        .takeIf(String::isNotBlank)

    private fun String.removeMarkdownPrefix(): String =
        replace(Regex("^(?:[-*+]\\s+|#{1,6}\\s+|>\\s+|\\d+(?:[.)]\\s+|、\\s*))"), "").trim()
}
