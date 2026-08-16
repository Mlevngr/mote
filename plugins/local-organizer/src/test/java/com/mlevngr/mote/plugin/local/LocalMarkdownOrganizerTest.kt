package com.mlevngr.mote.plugin.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMarkdownOrganizerTest {
    @Test fun organizesHeadingsBulletsTasksAndContinuousNumbers() {
        val source = """
            会议记录：
            • 第一项
            + 第二项
            TODO：联系客户
            4、步骤甲
            9. 步骤乙
            负责人： 小王
        """.trimIndent()

        assertEquals(
            """
                ## 会议记录
                - 第一项
                - 第二项
                - [ ] 联系客户
                1. 步骤甲
                2. 步骤乙
                **负责人：** 小王
            """.trimIndent(),
            LocalMarkdownOrganizer.organize(source)
        )
    }

    @Test fun preservesAttachmentsMoteMarkersAndCodeExactly() {
        val source = """
            ![图](assets/example.png)  
            <!-- mote:pdf-page-note asset="assets/a.pdf" page="1" -->
            ```kotlin
            val value = "TODO: untouched"  


            ```
        """.trimIndent()

        assertEquals(source, LocalMarkdownOrganizer.organize(source))
    }

    @Test fun taskExtractionAddsOneDeduplicatedOfflineSection() {
        val source = """
            # 项目
            TODO：提交报告
            - [ ] 提交报告
            需要 联系客户。
        """.trimIndent()

        val result = LocalMarkdownOrganizer.extractTasks(source)

        assertTrue(result.startsWith("## 行动项\n- [ ] 提交报告\n- [ ] 联系客户"))
        assertEquals(1, Regex("- \\[ ] 提交报告").findAll(result.substringBefore("\n\n# 项目")).count())
        assertFalse("网络" in result)
    }

    @Test fun cleanupDoesNotPromoteLabelsOrRewriteUrls() {
        val source = """
            说明：
            地址：https://example.test/a
            * 项目
        """.trimIndent()

        assertEquals(
            """
                说明：
                地址：https://example.test/a
                - 项目
            """.trimIndent(),
            LocalMarkdownOrganizer.cleanup(source)
        )
    }
}
