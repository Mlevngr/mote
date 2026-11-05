package com.mlevngr.inknote.appearance

import android.text.style.ForegroundColorSpan
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.core.CoreProps
import org.commonmark.node.Heading

/** Adds color after the existing heading span, retaining its size and font weight. */
class SkinHeadingPlugin(private val palette: SkinPalette) : AbstractMarkwonPlugin() {
    override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
        builder.appendFactory(Heading::class.java) { _, props ->
            ForegroundColorSpan(palette.heading(CoreProps.HEADING_LEVEL.require(props)))
        }
    }
}
