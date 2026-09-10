package io.github.kasecrab.razorback.ui.md

/**
 * Inline parser: code spans, emphasis with the CommonMark delimiter stack, strikethrough,
 * links, images and bare URLs. Soft breaks are kept as line breaks because that is how
 * people read chat replies.
 */
object MdInline {

    private sealed class Tok {
        class Text(val sb: StringBuilder) : Tok()
        class Node(val inline: Inline) : Tok()
        class Delim(val ch: Char, var count: Int, val canOpen: Boolean, val canClose: Boolean) : Tok()
    }

    fun parse(text: String): List<Inline> {
        if (text.isEmpty()) return emptyList()
        val toks = tokenize(text)
        resolveEmphasis(toks)
        return toInlines(toks)
    }

    private fun tokenize(s: String): ArrayList<Tok> {
        val toks = ArrayList<Tok>()
        var text: StringBuilder? = null
        fun textBuf(): StringBuilder {
            val t = text
            if (t != null) return t
            val n = StringBuilder()
            text = n
            toks.add(Tok.Text(n))
            return n
        }
        fun node(inl: Inline) {
            text = null
            toks.add(Tok.Node(inl))
        }
        var i = 0
        val n = s.length
        while (i < n) {
            val c = s[i]
            when {
                c == '\\' && i + 1 < n && isPunct(s[i + 1]) -> {
                    textBuf().append(s[i + 1])
                    i += 2
                }
                c == '\\' && i + 1 < n && s[i + 1] == '\n' -> {
                    node(Inline.HardBreak)
                    i += 2
                }
                c == '\n' -> {
                    val t = text
                    if (t != null && t.length >= 2 && t.endsWith("  ")) {
                        while (t.isNotEmpty() && t.last() == ' ') t.setLength(t.length - 1)
                        node(Inline.HardBreak)
                    } else {
                        node(Inline.SoftBreak)
                    }
                    i++
                    while (i < n && s[i] == ' ') i++
                }
                c == '`' -> {
                    var run = 0
                    while (i + run < n && s[i + run] == '`') run++
                    val close = findRun(s, i + run, '`', run)
                    if (close < 0) {
                        textBuf().append(s, i, i + run)
                        i += run
                    } else {
                        var code = s.substring(i + run, close).replace('\n', ' ')
                        if (code.length >= 2 && code.startsWith(" ") && code.endsWith(" ") && code.isNotBlank()) code = code.substring(1, code.length - 1)
                        node(Inline.Code(code))
                        i = close + run
                    }
                }
                c == '!' && i + 1 < n && s[i + 1] == '[' -> {
                    val r = link(s, i + 1)
                    if (r == null) {
                        textBuf().append('!')
                        i++
                    } else {
                        node(Inline.Image(s.substring(i + 2, r.closeBracket), r.url))
                        i = r.end
                    }
                }
                c == '[' -> {
                    val r = link(s, i)
                    if (r == null) {
                        textBuf().append('[')
                        i++
                    } else {
                        node(Inline.Link(parse(s.substring(i + 1, r.closeBracket)), r.url))
                        i = r.end
                    }
                }
                c == '<' && (s.startsWith("<http://", i) || s.startsWith("<https://", i)) -> {
                    val end = s.indexOf('>', i)
                    if (end < 0) {
                        textBuf().append('<')
                        i++
                    } else {
                        val url = s.substring(i + 1, end)
                        node(Inline.Link(listOf(Inline.Text(url)), url))
                        i = end + 1
                    }
                }
                (c == 'h' && (s.startsWith("http://", i) || s.startsWith("https://", i))) && (i == 0 || !s[i - 1].isLetterOrDigit()) -> {
                    var e = i
                    while (e < n && !s[e].isWhitespace() && s[e] != '<' && s[e] != '>') e++
                    var url = s.substring(i, e)
                    while (url.isNotEmpty() && (url.last() in ".,;:!?\"'" || (url.last() == ')' && url.count { it == '(' } < url.count { it == ')' }))) url = url.dropLast(1)
                    if (url.length <= 8) {
                        textBuf().append(c)
                        i++
                    } else {
                        node(Inline.Link(listOf(Inline.Text(url)), url))
                        i += url.length
                    }
                }
                c == '*' || c == '_' || c == '~' -> {
                    var run = 0
                    while (i + run < n && s[i + run] == c) run++
                    val prev = if (i == 0) ' ' else s[i - 1]
                    val next = if (i + run >= n) ' ' else s[i + run]
                    val leftFlank = !next.isWhitespace() && (!isPunct(next) || prev.isWhitespace() || isPunct(prev))
                    val rightFlank = !prev.isWhitespace() && (!isPunct(prev) || next.isWhitespace() || isPunct(next))
                    var canOpen = leftFlank
                    var canClose = rightFlank
                    if (c == '_') {
                        canOpen = leftFlank && (!rightFlank || isPunct(prev))
                        canClose = rightFlank && (!leftFlank || isPunct(next))
                    }
                    if (c == '~' && run != 2) {
                        textBuf().append(s, i, i + run)
                    } else if (!canOpen && !canClose) {
                        textBuf().append(s, i, i + run)
                    } else {
                        text = null
                        toks.add(Tok.Delim(c, run, canOpen, canClose))
                    }
                    i += run
                }
                else -> {
                    textBuf().append(c)
                    i++
                }
            }
        }
        return toks
    }

    private class LinkMatch(val closeBracket: Int, val url: String, val end: Int)

    /** `[text](url "title")` starting at the bracket; null when it is not a link. */
    private fun link(s: String, open: Int): LinkMatch? {
        var depth = 0
        var k = open
        while (k < s.length) {
            when (s[k]) {
                '\\' -> k++
                '`' -> {
                    val run = run(s, k, '`')
                    val close = findRun(s, k + run, '`', run)
                    if (close >= 0) k = close + run - 1
                }
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) break
                }
            }
            k++
        }
        if (k >= s.length || k + 1 >= s.length || s[k + 1] != '(') return null
        var p = k + 2
        var paren = 0
        val urlStart = p
        while (p < s.length) {
            val c = s[p]
            if (c == '\\') {
                p += 2
                continue
            }
            if (c == '(') paren++
            if (c == ')') {
                if (paren == 0) break
                paren--
            }
            if (c.isWhitespace()) break
            p++
        }
        if (p >= s.length) return null
        val url = s.substring(urlStart, p)
        var q = p
        if (s[q].isWhitespace()) {
            q = s.indexOf(')', q)
            if (q < 0) return null
        }
        return LinkMatch(k, url, q + 1)
    }

    private fun run(s: String, at: Int, ch: Char): Int {
        var r = 0
        while (at + r < s.length && s[at + r] == ch) r++
        return r
    }

    private fun findRun(s: String, from: Int, ch: Char, len: Int): Int {
        var k = from
        while (k < s.length) {
            if (s[k] == ch) {
                val r = run(s, k, ch)
                if (r == len) return k
                k += r
            } else {
                k++
            }
        }
        return -1
    }

    private fun isPunct(c: Char): Boolean = c in "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"

    private fun resolveEmphasis(toks: ArrayList<Tok>) {
        var c = 0
        while (c < toks.size) {
            val closer = toks[c] as? Tok.Delim
            if (closer == null || !closer.canClose) {
                c++
                continue
            }
            var o = c - 1
            var opener: Tok.Delim? = null
            while (o >= 0) {
                val t = toks[o] as? Tok.Delim
                if (t != null && t.ch == closer.ch && t.canOpen && t.count > 0 && !(closer.ch == '~' && t.count < 2)) {
                    opener = t
                    break
                }
                o--
            }
            if (opener == null) {
                c++
                continue
            }
            val use = if (closer.ch == '~') 2 else if (opener.count >= 2 && closer.count >= 2) 2 else 1
            val inner = ArrayList<Tok>(toks.subList(o + 1, c))
            val children = toInlines(inner)
            val node: Inline = when {
                closer.ch == '~' -> Inline.Strike(children)
                use == 2 -> Inline.Strong(children)
                else -> Inline.Em(children)
            }
            // Replace everything between opener and closer with the node.
            for (k in c - 1 downTo o + 1) toks.removeAt(k)
            toks.add(o + 1, Tok.Node(node))
            opener.count -= use
            closer.count -= use
            var closerIndex = o + 2
            if (opener.count == 0) {
                toks.removeAt(o)
                closerIndex--
            }
            if (closer.count == 0) {
                toks.removeAt(closerIndex)
                c = closerIndex
            } else {
                c = closerIndex
            }
        }
    }

    private fun toInlines(toks: List<Tok>): List<Inline> {
        val out = ArrayList<Inline>(toks.size)
        val pending = StringBuilder()
        fun flush() {
            if (pending.isNotEmpty()) {
                out.add(Inline.Text(pending.toString()))
                pending.setLength(0)
            }
        }
        for (t in toks) {
            when (t) {
                is Tok.Text -> pending.append(t.sb)
                is Tok.Delim -> repeat(t.count) { pending.append(t.ch) }
                is Tok.Node -> {
                    flush()
                    out.add(t.inline)
                }
            }
        }
        flush()
        return out
    }
}
