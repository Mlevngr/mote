package com.mlevngr.inknote.markdown

data class MarkdownHistoryState(
    val markdown: String,
    val activeLine: Int?,
    val selectionStart: Int,
    val selectionEnd: Int
)

enum class MarkdownHistoryKind {
    Insert,
    Delete,
    Replace,
    Structural;

    val canMerge: Boolean get() = this != Structural
}

/**
 * Document-level undo history for the hybrid line editor. Nearby edits of the same kind and line
 * are grouped, while structural operations always form their own undo event.
 */
class MarkdownHistory(
    initialState: MarkdownHistoryState,
    private val maxEvents: Int = DEFAULT_MAX_EVENTS,
    private val mergeDelayMillis: Long = DEFAULT_MERGE_DELAY_MILLIS
) {
    private val states = mutableListOf(initialState)
    private var position = 0
    private var lastEdit: EditMetadata? = null

    val canUndo: Boolean get() = position > 0
    val canRedo: Boolean get() = position < states.lastIndex

    fun retainedMarkdown(): String = states.joinToString("\n") { it.markdown }

    fun updateCurrentState(state: MarkdownHistoryState): Boolean {
        if (state.markdown != states[position].markdown) return false
        states[position] = state
        return true
    }

    fun record(
        state: MarkdownHistoryState,
        kind: MarkdownHistoryKind,
        line: Int?,
        timestampMillis: Long
    ): Boolean {
        if (state == states[position]) return false
        if (position < states.lastIndex) states.subList(position + 1, states.size).clear()

        val edit = EditMetadata(kind, line, timestampMillis)
        val merge = kind.canMerge && lastEdit?.let { previous ->
            previous.kind == kind &&
                previous.line == line &&
                timestampMillis - previous.timestampMillis in 0..mergeDelayMillis &&
                position == states.lastIndex
        } == true

        if (merge) {
            states[position] = state
        } else {
            states.add(state)
            position++
            trimOldestStates()
        }
        lastEdit = edit
        return true
    }

    fun undo(): MarkdownHistoryState? {
        if (!canUndo) return null
        lastEdit = null
        position--
        return states[position]
    }

    fun redo(): MarkdownHistoryState? {
        if (!canRedo) return null
        lastEdit = null
        position++
        return states[position]
    }

    fun breakGroup() {
        lastEdit = null
    }

    private fun trimOldestStates() {
        val maximumStates = maxEvents.coerceAtLeast(1) + 1
        while (states.size > maximumStates) {
            states.removeAt(0)
            position--
        }
    }

    private data class EditMetadata(
        val kind: MarkdownHistoryKind,
        val line: Int?,
        val timestampMillis: Long
    )

    companion object {
        const val DEFAULT_MAX_EVENTS = 100
        const val DEFAULT_MERGE_DELAY_MILLIS = 500L
    }
}
