package com.mlevngr.inknote.ui

import com.mlevngr.inknote.markdown.PdfPageNotes

class HybridRowFactory(
    private val createPreviews: (String) -> List<PreviewRow>
) {
    constructor(previewFactory: PreviewRowFactory) : this(previewFactory::create)

    fun create(lines: List<String>, activeLine: Int?): List<HybridRow> = buildList {
        var index = 0
        while (index < lines.size) {
            val source = lines[index]
            val previews = createPreviews(source)
            val pdfPages = previews.filterIsInstance<PreviewRow.PdfPage>()
            if (pdfPages.isEmpty()) {
                addLine(index, source, activeLine, null, previews)
                index++
                continue
            }

            val section = PdfPageNotes.sectionAt(lines, index)
            val instanceKey = pdfPages.first().instanceKey
                ?: "legacy:$index:${pdfPages.first().file.canonicalPath}"
            if (index == activeLine) {
                add(HybridRow.Editor(index, source))
            } else {
                pdfPages.forEach { page ->
                    val context = PdfRowContext(instanceKey, index, page.pageIndex)
                    add(HybridRow.Rendered(index, page.copy(instanceKey = instanceKey), context))
                    section?.blocks
                        ?.filter { it.anchor.pageIndex == page.pageIndex }
                        ?.forEach { block ->
                            block.contentLines.forEach { noteLine ->
                                addLine(
                                    noteLine,
                                    lines[noteLine],
                                    activeLine,
                                    context,
                                    createPreviews(lines[noteLine])
                                )
                            }
                        }
                }
            }
            index = section?.endExclusive ?: (index + 1)
        }
    }

    private fun MutableList<HybridRow>.addLine(
        lineIndex: Int,
        source: String,
        activeLine: Int?,
        context: PdfRowContext?,
        previews: List<PreviewRow>
    ) {
        if (lineIndex == activeLine) {
            add(HybridRow.Editor(lineIndex, source, context))
        } else if (previews.isEmpty()) {
            add(HybridRow.Rendered(lineIndex, PreviewRow.Markdown("\u00a0"), context))
        } else {
            previews.forEach { add(HybridRow.Rendered(lineIndex, it, context)) }
        }
    }
}
