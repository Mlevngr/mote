package com.mlevngr.mote.plugin.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class AiTextProtocolTest {
    @Test
    fun `normalizes base endpoint to chat completions`() {
        assertEquals(
            "https://example.test/v1/chat/completions",
            AiTextProtocol.completionsUrl(" https://example.test/ ")
        )
        assertEquals(
            "https://example.test/v1/chat/completions",
            AiTextProtocol.completionsUrl("https://example.test/v1/chat/completions")
        )
    }

    @Test
    fun `removes optional markdown code fence`() {
        assertEquals("# Title", AiTextProtocol.stripMarkdownFence("```markdown\n# Title\n```"))
        assertEquals("# Title", AiTextProtocol.stripMarkdownFence("# Title"))
    }
}
