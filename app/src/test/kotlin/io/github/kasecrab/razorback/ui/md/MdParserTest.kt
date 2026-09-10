package io.github.kasecrab.razorback.ui.md

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MdParserTest {

    private fun dump(blocks: List<MdBlock>): String = blocks.joinToString(" ") { dump(it) }

    private fun dump(b: MdBlock): String = when (b) {
        is MdBlock.Paragraph -> "p[" + dumpInl(b.inlines) + "]"
        is MdBlock.Heading -> "h${b.level}[" + dumpInl(b.inlines) + "]"
        is MdBlock.Fence -> "fence(${b.lang ?: "-"},${if (b.closed) "closed" else "open"})[" + b.code + "]"
        is MdBlock.ListBlock -> (if (b.ordered) "ol(${b.start})" else "ul") + "[" + b.items.joinToString("|") { dump(it.blocks) } + "]"
        is MdBlock.Quote -> "q[" + dump(b.blocks) + "]"
        is MdBlock.Table -> "table(" + b.aligns.joinToString(",") { it.name.take(1) } + ")[" + b.header.joinToString("|") { dumpInl(it) } + " / " + b.rows.joinToString(" ; ") { r -> r.joinToString("|") { dumpInl(it) } } + "]"
        is MdBlock.Image -> "img(${b.alt},${b.url})"
        MdBlock.Rule -> "hr"
    }

    private fun dumpInl(inl: List<Inline>): String = inl.joinToString("") {
        when (it) {
            is Inline.Text -> it.text
            is Inline.Code -> "`" + it.code + "`"
            is Inline.Strong -> "<b>" + dumpInl(it.children) + "</b>"
            is Inline.Em -> "<i>" + dumpInl(it.children) + "</i>"
            is Inline.Strike -> "<s>" + dumpInl(it.children) + "</s>"
            is Inline.Link -> "<a " + it.url + ">" + dumpInl(it.children) + "</a>"
            is Inline.Image -> "<img " + it.url + ">"
            Inline.SoftBreak -> "⏎"
            Inline.HardBreak -> "⏎!"
        }
    }

    private fun md(s: String) = dump(MdParser.parse(s))

    @Test
    fun headingsAndParagraphs() {
        assertEquals("h1[Title] p[Body text] h3[Sub]", md("# Title\n\nBody text\n### Sub ###"))
        assertEquals("h2[Setext]", md("Setext\n---"))
        assertEquals("hr", md("---"))
    }

    @Test
    fun emphasisNesting() {
        assertEquals("p[<b>bold</b> and <i>it</i>]", md("**bold** and *it*"))
        assertEquals("p[<b>bold <i>it</i> bold</b>]", md("**bold *it* bold**"))
        assertEquals("p[<i>a <b>b</b> c</i>]", md("*a **b** c*"))
        assertEquals("p[<i><b>x</b></i>]", md("***x***"))
        assertEquals("p[snake_case_name]", md("snake_case_name"))
        assertEquals("p[<s>gone</s>]", md("~~gone~~"))
        assertEquals("p[2 * 3 * 4]", md("2 * 3 * 4"))
        assertEquals("p[*unclosed]", md("*unclosed"))
    }

    @Test
    fun inlineCodeKeepsAsterisks() {
        assertEquals("p[use `a*b*c` here]", md("use `a*b*c` here"))
        assertEquals("p[`a ` b` tick]", md("``a ` b`` tick"))
    }

    @Test
    fun fencesOpenAndClosed() {
        assertEquals("fence(kotlin,closed)[val x = 1]", md("```kotlin\nval x = 1\n```"))
        assertEquals("fence(-,open)[still typing]", md("```\nstill typing"))
        assertEquals("fence(-,closed)[a\n```inner\nb]", md("````\na\n```inner\nb\n````"))
        assertEquals("fence(py,closed)[x]", md("~~~py\nx\n~~~"))
    }

    @Test
    fun lists() {
        assertEquals("ul[p[a]|p[b]]", md("- a\n- b"))
        assertEquals("ol(3)[p[x]|p[y]]", md("3. x\n4. y"))
        assertEquals("ul[p[a] ul[p[b]|p[c]]|p[d]]", md("- a\n  - b\n  - c\n- d"))
        assertEquals("ul[p[a] ul[p[b]]]", md("- a\n    - b"))
        assertEquals("ul[p[para one] p[para two]]", md("- para one\n\n  para two"))
        assertEquals("ul[p[a⏎lazy]]", md("- a\nlazy"))
        assertEquals("ul[p[a] fence(-,closed)[code]]", md("- a\n\n  ```\n  code\n  ```"))
    }

    @Test
    fun quotes() {
        assertEquals("q[p[hi⏎there]]", md("> hi\n> there"))
        assertEquals("q[p[a] ul[p[b]]]", md("> a\n>\n> - b"))
    }

    @Test
    fun tables() {
        assertEquals(
            "table(L,R)[Name|Qty / a|1 ; b|]",
            md("| Name | Qty |\n|---|---:|\n| a | 1 |\n| b |"),
        )
        assertEquals("p[not | a table]", md("not | a table"))
    }

    @Test
    fun linksAndImages() {
        assertEquals("p[see <a https://x.y/z?q=(1)>docs</a>.]", md("see [docs](https://x.y/z?q=(1))."))
        assertEquals("p[<a https://a.b/c>https://a.b/c</a>, next]", md("https://a.b/c, next"))
        assertEquals("p[<a https://a.b>https://a.b</a>]", md("<https://a.b>"))
        assertEquals("img(alt,https://i/p.png)", md("![alt](https://i/p.png)"))
        assertEquals("p[[not a link]]", md("[not a link]"))
    }

    @Test
    fun breaks() {
        assertEquals("p[a⏎b]", md("a\nb"))
        assertEquals("p[a⏎!b]", md("a  \nb"))
        assertEquals("p[a⏎!b]", md("a\\\nb"))
        assertEquals("p[a*b]", md("a\\*b"))
    }

    @Test
    fun topLevelSourcesCoverInput() {
        val text = "# T\n\npara\n\n```\ncode"
        val top = MdParser.parseTop(text)
        assertEquals(listOf("# T", "para", "```\ncode"), top.map { it.source })
        assertTrue(top.last().block is MdBlock.Fence)
    }

    @Test
    fun streamingPrefixesParseWithoutThrowing() {
        val text = "Intro **bold\n\n- item `code\n- two\n\n| a | b |\n|---|---|\n| 1 |\n\n```js\nlet x = [1,\n"
        for (i in 0..text.length) MdParser.parseTop(text.substring(0, i))
    }

    @Test
    fun pathologicalInputIsFast() {
        val stars = "*".repeat(5000) + "a" + "_".repeat(5000)
        val t0 = System.nanoTime()
        MdParser.parse(stars)
        assertTrue((System.nanoTime() - t0) / 1e6 < 200)
    }
}
