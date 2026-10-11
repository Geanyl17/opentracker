package io.github.geanyl17.opentracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** Renders the Markdown subset that reports use: headings, paragraphs, lists, emphasis and rules. */
@Composable
fun Markdown(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { MarkdownBlocks.parse(text) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlocks.Heading -> Text(
                    inline(block.text),
                    style = if (block.level <= 2) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                is MarkdownBlocks.Paragraph -> Text(inline(block.text), style = MaterialTheme.typography.bodyLarge)
                is MarkdownBlocks.ListItem -> Row(Modifier.padding(start = (block.depth * 16).dp)) {
                    Text(block.marker, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.widthIn(min = 20.dp))
                    Text(inline(block.text), style = MaterialTheme.typography.bodyLarge)
                }
                is MarkdownBlocks.Code -> Text(
                    block.text,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(12.dp),
                )
                MarkdownBlocks.Rule -> HorizontalDivider(Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

object MarkdownBlocks {
    sealed interface Block
    data class Heading(val level: Int, val text: String) : Block
    data class Paragraph(val text: String) : Block
    data class ListItem(val marker: String, val text: String, val depth: Int) : Block
    data class Code(val text: String) : Block
    data object Rule : Block

    private val heading = Regex("""^(#{1,6})\s+(.*)$""")
    private val bullet = Regex("""^(\s*)[-*+]\s+(.*)$""")
    private val numbered = Regex("""^(\s*)(\d+)[.)]\s+(.*)$""")
    private val rule = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")
    private val tableDivider = Regex("""^\s*\|?[\s:|-]+\|?\s*$""")

    fun parse(markdown: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val paragraph = mutableListOf<String>()
        var code: MutableList<String>? = null

        fun flush() {
            if (paragraph.isNotEmpty()) blocks += Paragraph(paragraph.joinToString(" "))
            paragraph.clear()
        }

        for (line in markdown.lines()) {
            val open = code
            if (open != null) {
                if (line.trimStart().startsWith("```")) {
                    blocks += Code(open.joinToString("\n"))
                    code = null
                } else {
                    open += line
                }
                continue
            }
            val trimmed = line.trim()
            when {
                trimmed.startsWith("```") -> {
                    flush()
                    code = mutableListOf()
                }
                trimmed.isEmpty() -> flush()
                rule.matches(trimmed) -> {
                    flush()
                    blocks += Rule
                }
                heading.matches(trimmed) -> {
                    flush()
                    val (hashes, text) = heading.find(trimmed)!!.destructured
                    blocks += Heading(hashes.length, text.trimEnd('#', ' '))
                }
                bullet.matches(line) -> {
                    flush()
                    val (indent, text) = bullet.find(line)!!.destructured
                    blocks += ListItem("•", text, indent.length / 2)
                }
                numbered.matches(line) -> {
                    flush()
                    val (indent, number, text) = numbered.find(line)!!.destructured
                    blocks += ListItem("$number.", text, indent.length / 2)
                }
                trimmed.startsWith("|") -> {
                    flush()
                    if (!tableDivider.matches(trimmed)) {
                        blocks += Paragraph(trimmed.trim('|').split('|').joinToString("  ·  ") { it.trim() })
                    }
                }
                else -> paragraph += trimmed
            }
        }
        code?.let { blocks += Code(it.joinToString("\n")) }
        flush()
        return blocks
    }
}

private val inlinePattern = Regex("""\*\*(.+?)\*\*|__(.+?)__|`([^`]+)`|\[([^\]]+)]\([^)]*\)|\*([^*\s][^*]*?)\*""")

fun inline(text: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (match in inlinePattern.findAll(text)) {
        append(text, last, match.range.first)
        val (bold, boldAlt, code, link, italic) = match.destructured
        when {
            bold.isNotEmpty() || boldAlt.isNotEmpty() ->
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(bold.ifEmpty { boldAlt }) }
            code.isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(code) }
            link.isNotEmpty() -> append(link)
            else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(italic) }
        }
        last = match.range.last + 1
    }
    append(text, last, text.length)
}
