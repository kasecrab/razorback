package io.github.kasecrab.razorback.ui.md

/**
 * CommonMark-flavoured block parser tuned for model output: fences, headings, lists,
 * quotes, tables, rules and paragraphs. An unterminated fence is returned open so a reply
 * that is still streaming renders as code from its first line.
 */
object MdParser {

    private val FENCE_OPEN = Regex("^ {0,3}(`{3,}|~{3,})[ \\t]*([^\\s`]*).*$")
    private val ATX = Regex("^ {0,3}(#{1,6})(?:[ \\t]+(.*?))?[ \\t]*(?:#+[ \\t]*)?$")
    private val LIST_ITEM = Regex("^( {0,3})([-*+]|\\d{1,9}[.)])(?:( {1,4})(.*)|[ \\t]*)$")
    private val QUOTE = Regex("^ {0,3}> ?(.*)$")
    private val SETEXT = Regex("^ {0,3}(=+|-+)[ \\t]*$")
    private val IMAGE_LINE = Regex("^ {0,3}!\\[([^\\]]*)]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)[ \\t]*$")

    fun parse(text: String): List<MdBlock> = Cursor(text.lines()).parseAll()

    /** Top-level blocks with their source text; the last block's source is what streaming appends to. */
    fun parseTop(text: String): List<TopBlock> {
        val lines = text.lines()
        val c = Cursor(lines)
        val out = ArrayList<TopBlock>()
        while (true) {
            c.skipBlank()
            if (c.done) break
            val start = c.i
            val block = c.next() ?: break
            val end = c.i
            out.add(TopBlock(block, lines.subList(start, end).joinToString("\n")))
        }
        return out
    }

    private class Cursor(val lines: List<String>) {
        var i = 0
        val done: Boolean get() = i >= lines.size

        fun parseAll(): List<MdBlock> {
            val out = ArrayList<MdBlock>()
            while (true) {
                skipBlank()
                if (done) break
                out.add(next() ?: break)
            }
            return out
        }

        fun skipBlank() {
            while (!done && lines[i].isBlank()) i++
        }

        fun next(): MdBlock? {
            if (done) return null
            val line = lines[i]
            FENCE_OPEN.matchEntire(line)?.let { return fence(it) }
            ATX.matchEntire(line)?.let {
                i++
                return MdBlock.Heading(it.groupValues[1].length, MdInline.parse(it.groupValues[2]))
            }
            if (isRule(line)) {
                i++
                return MdBlock.Rule
            }
            IMAGE_LINE.matchEntire(line)?.let {
                i++
                return MdBlock.Image(it.groupValues[1], it.groupValues[2])
            }
            if (QUOTE.matches(line)) return quote()
            LIST_ITEM.matchEntire(line)?.let { return list(it) }
            if (isTableStart()) return table()
            return paragraph()
        }

        private fun fence(m: MatchResult): MdBlock {
            val marker = m.groupValues[1]
            val lang = m.groupValues[2].ifEmpty { null }
            val indent = lines[i].indexOf(marker[0])
            i++
            val body = StringBuilder()
            var closed = false
            while (!done) {
                val l = lines[i]
                val t = l.trimStart()
                if (t.startsWith(marker) && t.trimEnd().all { it == marker[0] }) {
                    closed = true
                    i++
                    break
                }
                var strip = 0
                while (strip < indent && strip < l.length && l[strip] == ' ') strip++
                if (body.isNotEmpty()) body.append('\n')
                body.append(l, strip, l.length)
                i++
            }
            return MdBlock.Fence(lang, body.toString(), closed)
        }

        private fun quote(): MdBlock {
            val inner = ArrayList<String>()
            while (!done) {
                val m = QUOTE.matchEntire(lines[i])
                if (m != null) {
                    inner.add(m.groupValues[1])
                } else if (lines[i].isNotBlank() && inner.isNotEmpty() && !startsBlock(lines[i]) && inner.last().isNotBlank()) {
                    inner.add(lines[i]) // lazy continuation of a paragraph inside the quote
                } else {
                    break
                }
                i++
            }
            return MdBlock.Quote(Cursor(inner).parseAll())
        }

        private fun list(first: MatchResult): MdBlock {
            val marker = first.groupValues[2]
            val ordered = marker[0].isDigit()
            val start = if (ordered) marker.dropLast(1).toIntOrNull() ?: 1 else 1
            val items = ArrayList<ListItem>()
            val baseIndent = first.groupValues[1].length
            while (!done) {
                val m = LIST_ITEM.matchEntire(lines[i]) ?: break
                if (m.groupValues[1].length != baseIndent) break
                val mk = m.groupValues[2]
                if (ordered != mk[0].isDigit()) break
                if (!ordered && mk[0] != marker[0]) break
                val contentIndent = baseIndent + mk.length + maxOf(1, m.groupValues[3].length)
                val itemLines = ArrayList<String>()
                itemLines.add(m.groupValues[4])
                i++
                while (!done) {
                    val l = lines[i]
                    if (l.isBlank()) {
                        // A blank line ends the item unless indented content follows.
                        if (i + 1 < lines.size && indentOf(lines[i + 1]) >= contentIndent) {
                            itemLines.add("")
                            i++
                            continue
                        }
                        break
                    }
                    if (indentOf(l) >= contentIndent) {
                        itemLines.add(l.substring(minOf(contentIndent, l.length)))
                        i++
                    } else if (!LIST_ITEM.matches(l) && !startsBlock(l) && itemLines.last().isNotBlank()) {
                        itemLines.add(l.trimStart()) // lazy continuation
                        i++
                    } else {
                        break
                    }
                }
                items.add(ListItem(Cursor(itemLines).parseAll()))
                if (!done && lines[i].isBlank()) {
                    // Blank line between items is allowed; the loop's item check decides.
                    var j = i
                    while (j < lines.size && lines[j].isBlank()) j++
                    if (j < lines.size && LIST_ITEM.matchEntire(lines[j])?.groupValues?.get(1)?.length == baseIndent) i = j else break
                }
            }
            return MdBlock.ListBlock(ordered, start, items)
        }

        private fun isTableStart(): Boolean =
            lines[i].contains('|') && i + 1 < lines.size && isTableDelim(lines[i + 1])

        private fun table(): MdBlock {
            val header = cells(lines[i]).map { MdInline.parse(it) }
            val aligns = cells(lines[i + 1]).map { spec ->
                val s = spec.trim()
                when {
                    s.startsWith(':') && s.endsWith(':') -> Align.CENTER
                    s.endsWith(':') -> Align.RIGHT
                    else -> Align.LEFT
                }
            }
            i += 2
            val rows = ArrayList<List<List<Inline>>>()
            while (!done && lines[i].isNotBlank() && lines[i].contains('|') && !startsBlock(lines[i])) {
                val row = cells(lines[i]).map { MdInline.parse(it) }
                rows.add(if (row.size >= header.size) row.take(header.size) else row + List(header.size - row.size) { emptyList() })
                i++
            }
            return MdBlock.Table(header, aligns, rows)
        }

        private fun cells(line: String): List<String> {
            var s = line.trim()
            if (s.startsWith("|")) s = s.substring(1)
            if (s.endsWith("|") && !s.endsWith("\\|")) s = s.substring(0, s.length - 1)
            val out = ArrayList<String>()
            val cur = StringBuilder()
            var k = 0
            var inCode = false
            while (k < s.length) {
                val ch = s[k]
                when {
                    ch == '\\' && k + 1 < s.length && s[k + 1] == '|' -> {
                        cur.append('|')
                        k++
                    }
                    ch == '`' -> {
                        inCode = !inCode
                        cur.append(ch)
                    }
                    ch == '|' && !inCode -> {
                        out.add(cur.toString().trim())
                        cur.setLength(0)
                    }
                    else -> cur.append(ch)
                }
                k++
            }
            out.add(cur.toString().trim())
            return out
        }

        private fun paragraph(): MdBlock {
            val buf = StringBuilder()
            var first = true
            while (!done) {
                val l = lines[i]
                if (l.isBlank()) break
                if (!first) {
                    SETEXT.matchEntire(l)?.let {
                        i++
                        return MdBlock.Heading(if (it.groupValues[1][0] == '=') 1 else 2, MdInline.parse(buf.toString()))
                    }
                    if (startsBlock(l) || (LIST_ITEM.matches(l) && !l.trimStart().startsWith("+"))) break
                    if (isTableStart()) break
                    buf.append('\n')
                }
                buf.append(l.trimStart())
                first = false
                i++
            }
            return MdBlock.Paragraph(MdInline.parse(buf.toString().trimEnd()))
        }

        /** Lines that interrupt a paragraph. */
        private fun startsBlock(l: String): Boolean =
            FENCE_OPEN.matches(l) || ATX.matches(l) || isRule(l) || QUOTE.matches(l) || IMAGE_LINE.matches(l)

        /** Three or more of one of - * _ with only blanks between; loops, so a long line cannot blow the stack. */
        private fun isRule(l: String): Boolean {
            var k = 0
            while (k < l.length && l[k] == ' ') k++
            if (k > 3 || k >= l.length) return false
            val ch = l[k]
            if (ch != '-' && ch != '*' && ch != '_') return false
            var count = 0
            while (k < l.length) {
                val c = l[k]
                if (c == ch) count++ else if (c != ' ' && c != '\t') return false
                k++
            }
            return count >= 3
        }

        /** `|---|:--:|` style separator line under a table header. */
        private fun isTableDelim(l: String): Boolean {
            val s = l.trim()
            if (!s.contains('-')) return false
            var any = false
            for (part in s.split('|')) {
                val cell = part.trim()
                if (cell.isEmpty()) continue
                var a = 0
                var b = cell.length
                if (cell[a] == ':') a++
                if (b > a && cell[b - 1] == ':') b--
                if (b <= a) return false
                for (k in a until b) if (cell[k] != '-') return false
                any = true
            }
            return any
        }

        private fun indentOf(l: String): Int {
            var n = 0
            while (n < l.length && l[n] == ' ') n++
            return n
        }
    }
}
