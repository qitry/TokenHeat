package com.tokenheat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pure Kotlin & Compose Markdown parser and renderer.
 * Formats AI text output with headers, lists, quotes, inline code,
 * collapsible reasoning chains, and syntax-highlighted code blocks.
 */
@Composable
fun MarkdownView(
    content: String,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
    onCopyCode: (String) -> Unit = {},
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val blocks = remember(content) { parseMarkdownBlocks(content) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        blocks.forEach { block ->
            RenderMarkdownBlock(
                block = block,
                isDark = isDark,
                onCopyCode = onCopyCode,
            )
        }

        if (isStreaming) {
            Text(
                text = "▋",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Claude / Codex style collapsible thinking block for deep reasoning chains.
 */
@Composable
fun ThinkingProcessCard(
    reasoningContent: String,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
) {
    // User can freely toggle expand/collapse even while streaming
    var isExpanded by remember { mutableStateOf(true) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Lucide.Brain,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isStreaming) "正在深度思考..." else "思考过程",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (isStreaming) {
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Lucide.ChevronUp else Lucide.ChevronDown,
                    contentDescription = if (isExpanded) "折叠" else "展开",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    SelectionContainer {
                        Text(
                            text = reasoningContent.trim(),
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            lineHeight = 20.sp,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Codex style MCP tool call invocation and execution result card.
 */
@Composable
fun ToolCallCard(
    toolName: String,
    arguments: String,
    result: String?,
    isExecuting: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Lucide.Wrench,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isExecuting) "调用工具: $toolName..." else "已执行工具: $toolName",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (isExecuting) {
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Lucide.ChevronUp else Lucide.ChevronDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    if (arguments.isNotBlank()) {
                        Text(
                            text = "输入参数:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        ) {
                            Text(
                                text = arguments,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(6.dp),
                            )
                        }
                    }

                    if (!result.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "输出结果:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        ) {
                            Text(
                                text = result.take(1500) + if (result.length > 1500) "\n...(已省略)" else "",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Syntax-highlighted code block card with language header and copy button.
 */
@Composable
private fun CodeBlockCard(
    language: String,
    code: String,
    isDark: Boolean,
    onCopy: () -> Unit,
) {
    var copied by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = if (isDark) ZincColors.Zinc900 else ZincColors.Zinc100,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isDark) ZincColors.Zinc950 else ZincColors.Zinc200)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = language.ifBlank { "code" }.lowercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.clickable {
                        onCopy()
                        copied = true
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (copied) Lucide.Check else Lucide.Copy,
                        contentDescription = "复制代码",
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (copied) "已复制" else "复制",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Code Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                SelectionContainer {
                    Text(
                        text = highlightCode(code, language, isDark),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 19.sp,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Renders an individual markdown block.
 * Supports nesting inside blockquotes (code blocks, paragraphs, lists, headers, dividers).
 */
@Composable
private fun RenderMarkdownBlock(
    block: MarkdownBlock,
    isDark: Boolean,
    onCopyCode: (String) -> Unit,
) {
    when (block) {
        is MarkdownBlock.Header -> {
            val typography = when (block.level) {
                1 -> MaterialTheme.typography.titleLarge
                2 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            }
            SelectionContainer {
                Text(
                    text = renderInlineMarkdown(block.text, isDark),
                    style = typography,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                )
            }
        }
        is MarkdownBlock.Code -> {
            CodeBlockCard(
                language = block.language,
                code = block.code,
                isDark = isDark,
                onCopy = { onCopyCode(block.code) },
            )
        }
        is MarkdownBlock.Quote -> {
            Surface(
                shape = SquircleCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.5.dp)
                            .heightIn(min = 24.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), RoundedCornerShape(2.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        block.children.forEach { child ->
                            RenderMarkdownBlock(
                                block = child,
                                isDark = isDark,
                                onCopyCode = onCopyCode,
                            )
                        }
                    }
                }
            }
        }
        is MarkdownBlock.ListItem -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = if (block.ordered) "${block.index}. " else "• ",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = (block.indent * 12).dp),
                )
                SelectionContainer {
                    Text(
                        text = renderInlineMarkdown(block.text, isDark),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 22.sp,
                    )
                }
            }
        }
        is MarkdownBlock.Paragraph -> {
            SelectionContainer {
                Text(
                    text = renderInlineMarkdown(block.text, isDark),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 22.sp,
                )
            }
        }
        is MarkdownBlock.Divider -> {
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            )
        }
    }
}

/** Markdown parser representation of document structure. */
private sealed class MarkdownBlock {
    data class Header(val level: Int, val text: String) : MarkdownBlock()
    data class Code(val language: String, val code: String) : MarkdownBlock()
    data class Quote(val children: List<MarkdownBlock>) : MarkdownBlock()
    data class ListItem(val text: String, val ordered: Boolean, val index: Int, val indent: Int) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    object Divider : MarkdownBlock()
}

/** Splits markdown text into typed blocks with support for nested blockquotes and dividers. */
private fun parseMarkdownBlocks(rawText: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = rawText.split("\n")
    var inCodeBlock = false
    var codeLang = ""
    val codeBuilder = StringBuilder()
    val paragraphBuilder = StringBuilder()

    fun flushParagraph() {
        if (paragraphBuilder.isNotBlank()) {
            blocks.add(MarkdownBlock.Paragraph(paragraphBuilder.toString().trim()))
            paragraphBuilder.clear()
        }
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            if (inCodeBlock) {
                // End code block
                blocks.add(MarkdownBlock.Code(codeLang, codeBuilder.toString()))
                codeBuilder.clear()
                codeLang = ""
                inCodeBlock = false
            } else {
                flushParagraph()
                inCodeBlock = true
                codeLang = trimmed.removePrefix("```").trim()
            }
            i++
            continue
        }

        if (inCodeBlock) {
            codeBuilder.append(line).append("\n")
            i++
            continue
        }

        // Horizontal divider: ---, ***, ___
        if (trimmed.matches(Regex("""^[-*_]{3,}$"""))) {
            flushParagraph()
            blocks.add(MarkdownBlock.Divider)
            i++
            continue
        }

        // Blockquote (supports multi-line, nested code blocks, etc.)
        if (trimmed.startsWith(">")) {
            flushParagraph()
            val quoteLines = mutableListOf<String>()
            while (i < lines.size) {
                val currentLine = lines[i]
                val currentTrimmed = currentLine.trim()
                if (currentTrimmed.startsWith(">")) {
                    val stripped = currentLine.trimStart().removePrefix(">").let {
                        if (it.startsWith(" ")) it.substring(1) else it
                    }
                    quoteLines.add(stripped)
                    i++
                } else {
                    break
                }
            }
            val children = parseMarkdownBlocks(quoteLines.joinToString("\n"))
            blocks.add(MarkdownBlock.Quote(children))
            continue
        }

        when {
            trimmed.startsWith("# ") -> {
                flushParagraph()
                blocks.add(MarkdownBlock.Header(1, trimmed.removePrefix("# ").trim()))
            }
            trimmed.startsWith("## ") -> {
                flushParagraph()
                blocks.add(MarkdownBlock.Header(2, trimmed.removePrefix("## ").trim()))
            }
            trimmed.startsWith("### ") -> {
                flushParagraph()
                blocks.add(MarkdownBlock.Header(3, trimmed.removePrefix("### ").trim()))
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                flushParagraph()
                val indent = line.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0) / 2
                blocks.add(MarkdownBlock.ListItem(trimmed.substring(2).trim(), ordered = false, index = 0, indent = indent))
            }
            trimmed.matches(Regex("""^\d+\.\s.*""")) -> {
                flushParagraph()
                val dotIdx = trimmed.indexOf('.')
                val num = trimmed.substring(0, dotIdx).toIntOrNull() ?: 1
                val itemText = trimmed.substring(dotIdx + 1).trim()
                val indent = line.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0) / 2
                blocks.add(MarkdownBlock.ListItem(itemText, ordered = true, index = num, indent = indent))
            }
            trimmed.isEmpty() -> {
                flushParagraph()
            }
            else -> {
                if (paragraphBuilder.isNotEmpty()) paragraphBuilder.append("\n")
                paragraphBuilder.append(line)
            }
        }
        i++
    }

    if (inCodeBlock) {
        blocks.add(MarkdownBlock.Code(codeLang, codeBuilder.toString()))
    }
    flushParagraph()

    return blocks
}

/**
 * Strips all raw Markdown syntax characters and formats inline styles:
 * - ***bold italic*** / ___bold italic___ -> Bold + Italic text
 * - **bold** / __bold__ -> Bold text (supports nested inline code)
 * - *italic* / _italic_ -> Italic text
 * - ~~strikethrough~~ -> Strikethrough text
 * - `code` -> Monospace rounded chip (inherits outer bold/italic)
 * - [label](url) -> Underlined primary colored label (URL stripped)
 * - Safe streaming fallback: strips unfinished leading asterisks/underscores
 */
private fun renderInlineMarkdown(
    rawText: String,
    isDark: Boolean,
    isParentBold: Boolean = false,
    isParentItalic: Boolean = false,
): AnnotatedString {
    // Strip redundant leading hash symbols if any slipped into the text
    val text = rawText.trimStart().replace(Regex("""^#{1,6}\s*"""), "")
    val primaryColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)

    return buildAnnotatedString {
        var i = 0
        val len = text.length

        while (i < len) {
            when {
                // 1. Inline code: `code`
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    val codeContent = if (end != -1) text.substring(i + 1, end) else text.substring(i + 1)
                    val nextI = if (end != -1) end + 1 else len

                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = if (isDark) ZincColors.Zinc800 else ZincColors.Zinc200,
                            fontSize = 12.5.sp,
                            fontWeight = if (isParentBold) FontWeight.Bold else FontWeight.Medium,
                            fontStyle = if (isParentItalic) FontStyle.Italic else FontStyle.Normal,
                            color = if (isDark) ZincColors.Zinc100 else ZincColors.Zinc900,
                        ),
                    ) {
                        append(" $codeContent ")
                    }
                    i = nextI
                }

                // 2. Bold Italic: ***text*** or ___text___
                (i + 2 < len && text.startsWith("***", i)) || (i + 2 < len && text.startsWith("___", i)) -> {
                    val delimiter = if (text.startsWith("***", i)) "***" else "___"
                    val end = text.indexOf(delimiter, i + 3)
                    val inner = if (end != -1) text.substring(i + 3, end) else text.substring(i + 3)
                    val nextI = if (end != -1) end + 3 else len

                    val sub = renderInlineMarkdown(inner, isDark, isParentBold = true, isParentItalic = true)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                        append(sub)
                    }
                    i = nextI
                }

                // 3. Bold: **text** or __text__ (supports bold inline code)
                (i + 1 < len && text.startsWith("**", i)) || (i + 1 < len && text.startsWith("__", i)) -> {
                    val delimiter = if (text.startsWith("**", i)) "**" else "__"
                    val end = text.indexOf(delimiter, i + 2)
                    val inner = if (end != -1) text.substring(i + 2, end) else text.substring(i + 2)
                    val nextI = if (end != -1) end + 2 else len

                    val sub = renderInlineMarkdown(inner, isDark, isParentBold = true, isParentItalic = isParentItalic)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(sub)
                    }
                    i = nextI
                }

                // 4. Strikethrough: ~~text~~
                i + 1 < len && text.startsWith("~~", i) -> {
                    val end = text.indexOf("~~", i + 2)
                    val inner = if (end != -1) text.substring(i + 2, end) else text.substring(i + 2)
                    val nextI = if (end != -1) end + 2 else len

                    val sub = renderInlineMarkdown(inner, isDark, isParentBold = isParentBold, isParentItalic = isParentItalic)
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(sub)
                    }
                    i = nextI
                }

                // 5. Italic: *text* or _text_
                (text[i] == '*' || text[i] == '_') -> {
                    val delimiter = text[i].toString()
                    val end = text.indexOf(delimiter, i + 1)
                    if (end != -1 && end > i + 1) {
                        val inner = text.substring(i + 1, end)
                        val sub = renderInlineMarkdown(inner, isDark, isParentBold = isParentBold, isParentItalic = true)
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(sub)
                        }
                        i = end + 1
                    } else {
                        if (i == len - 1) {
                            i++
                        } else {
                            append(text[i])
                            i++
                        }
                    }
                }

                // 6. Link: [label](url) -> Strip syntax, render label with underline
                text[i] == '[' -> {
                    val closeBracket = text.indexOf(']', i + 1)
                    val openParen = if (closeBracket != -1) text.indexOf('(', closeBracket) else -1
                    val closeParen = if (openParen == closeBracket + 1) text.indexOf(')', openParen) else -1

                    if (closeBracket != -1 && closeParen != -1 && openParen == closeBracket + 1) {
                        val label = text.substring(i + 1, closeBracket)
                        withStyle(
                            SpanStyle(
                                color = primaryColor,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Medium,
                            ),
                        ) {
                            append(label)
                        }
                        i = closeParen + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }

                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}

/** High performance keyword syntax highlighter for code snippets. */
private fun highlightCode(code: String, language: String, isDark: Boolean): AnnotatedString {
    val keywordColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
    val stringColor = if (isDark) Color(0xFF34D399) else Color(0xFF059669)
    val commentColor = if (isDark) ZincColors.Zinc500 else ZincColors.Zinc400
    val numberColor = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)

    val keywords = setOf(
        "val", "var", "fun", "class", "interface", "object", "return", "if", "else",
        "for", "while", "when", "import", "package", "override", "private", "public",
        "protected", "internal", "data", "enum", "sealed", "def", "lambda", "self",
        "const", "let", "function", "async", "await", "export", "from", "type",
        "struct", "impl", "trait", "fn", "pub", "mut", "use", "mod", "SELECT", "FROM",
        "WHERE", "INSERT", "UPDATE", "DELETE", "true", "false", "null", "nil", "None",
    )

    return buildAnnotatedString {
        val lines = code.split("\n")
        lines.forEachIndexed { lineIdx, line ->
            var idx = 0
            val lineLen = line.length

            while (idx < lineLen) {
                val c = line[idx]

                // Line comment // or #
                if ((c == '/' && idx + 1 < lineLen && line[idx + 1] == '/') || c == '#') {
                    withStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic)) {
                        append(line.substring(idx))
                    }
                    break
                }

                // String literal "..." or '...'
                if (c == '"' || c == '\'') {
                    val quoteChar = c
                    val startQuote = idx
                    idx++
                    while (idx < lineLen && line[idx] != quoteChar) {
                        if (line[idx] == '\\' && idx + 1 < lineLen) idx++
                        idx++
                    }
                    if (idx < lineLen) idx++
                    withStyle(SpanStyle(color = stringColor)) {
                        append(line.substring(startQuote, idx.coerceAtMost(lineLen)))
                    }
                    continue
                }

                // Identifier / keyword
                if (c.isLetter() || c == '_') {
                    val startWord = idx
                    while (idx < lineLen && (line[idx].isLetterOrDigit() || line[idx] == '_')) {
                        idx++
                    }
                    val word = line.substring(startWord, idx)
                    if (word in keywords) {
                        withStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.SemiBold)) {
                            append(word)
                        }
                    } else {
                        append(word)
                    }
                    continue
                }

                // Number
                if (c.isDigit()) {
                    val startNum = idx
                    while (idx < lineLen && (line[idx].isLetterOrDigit() || line[idx] == '.')) {
                        idx++
                    }
                    withStyle(SpanStyle(color = numberColor)) {
                        append(line.substring(startNum, idx))
                    }
                    continue
                }

                append(c)
                idx++
            }

            if (lineIdx < lines.size - 1) append("\n")
        }
    }
}
