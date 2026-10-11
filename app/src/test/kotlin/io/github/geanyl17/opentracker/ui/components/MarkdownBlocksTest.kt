package io.github.geanyl17.opentracker.ui.components

import io.github.geanyl17.opentracker.ui.components.MarkdownBlocks.Heading
import io.github.geanyl17.opentracker.ui.components.MarkdownBlocks.ListItem
import io.github.geanyl17.opentracker.ui.components.MarkdownBlocks.Paragraph
import io.github.geanyl17.opentracker.ui.components.MarkdownBlocks.Rule
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownBlocksTest {
    @Test
    fun parsesAReport() {
        val report = """
            ## This month
            You've spent **₱8,200** so far,
            which is on track.

            - Food: ₱4,200
              - mostly lunches
            1. Cook twice a week
            ---
            | Category | Amount |
            |---|---|
            | Food | ₱4,200 |
        """.trimIndent()

        assertEquals(
            listOf(
                Heading(2, "This month"),
                Paragraph("You've spent **₱8,200** so far, which is on track."),
                ListItem("•", "Food: ₱4,200", 0),
                ListItem("•", "mostly lunches", 1),
                ListItem("1.", "Cook twice a week", 0),
                Rule,
                Paragraph("Category  ·  Amount"),
                Paragraph("Food  ·  ₱4,200"),
            ),
            MarkdownBlocks.parse(report),
        )
    }
}
