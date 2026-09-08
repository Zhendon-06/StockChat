package com.guet.liang.stockchat.data

internal data class MermaidMindMapNode(
    val label: String,
    val children: List<MermaidMindMapNode>,
)

internal object MermaidMindMapParser {
    fun parse(source: String): MermaidMindMapNode? {
        val lines = source.lines()
        val rootLineIndex = lines.indexOfFirst { line ->
            line.trim().isNotEmpty() && !line.trim().equals(MERMAID_HEADER, ignoreCase = true)
        }
        if (rootLineIndex < 0 || lines.getOrNull(0)?.trim()?.equals(MERMAID_HEADER, ignoreCase = true) != true) {
            return null
        }

        val rootLine = lines[rootLineIndex]
        val rootIndent = indentation(rootLine)
        val root = MutableNode(decodeLabel(rootLine.trim(), isRoot = true))
        if (root.label.isBlank()) {
            return null
        }
        val stack = mutableListOf(IndentNode(rootIndent, root))
        lines.drop(rootLineIndex + 1).forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith(COMMENT_PREFIX)) {
                return@forEach
            }
            val lineIndent = indentation(line)
            if (lineIndent <= rootIndent) {
                return@forEach
            }
            while (stack.size > 1 && stack.last().indent >= lineIndent) {
                stack.removeAt(stack.lastIndex)
            }
            val parent = stack.lastOrNull()?.node ?: root
            val child = MutableNode(decodeLabel(trimmed, isRoot = false))
            if (child.label.isBlank()) {
                return@forEach
            }
            parent.children += child
            stack += IndentNode(lineIndent, child)
        }
        return root.freeze()
    }

    private fun indentation(line: String): Int {
        return line.takeWhile { character -> character == ' ' || character == '\t' }.length
    }

    private fun decodeLabel(line: String, isRoot: Boolean): String {
        var label = line.trim()
        if (isRoot && label.startsWith(ROOT_PREFIX, ignoreCase = true)) {
            label = label.substring(ROOT_PREFIX.length).trim()
        }
        SHAPE_WRAPPERS.firstOrNull { (start, end) ->
            label.startsWith(start) && label.endsWith(end) && label.length > start.length + end.length
        }?.let { (start, end) ->
            label = label.substring(start.length, label.length - end.length).trim()
        }
        return label
    }

    private class MutableNode(
        val label: String,
        val children: MutableList<MutableNode> = mutableListOf(),
    ) {
        fun freeze(): MermaidMindMapNode {
            return MermaidMindMapNode(
                label = label,
                children = children.map(MutableNode::freeze),
            )
        }
    }

    private data class IndentNode(
        val indent: Int,
        val node: MutableNode,
    )

    private const val MERMAID_HEADER = "mindmap"
    private const val ROOT_PREFIX = "root"
    private const val COMMENT_PREFIX = "%%"
    private val SHAPE_WRAPPERS = listOf(
        "((" to "))",
        "[" to "]",
        "(" to ")",
        "{" to "}",
    )
}
