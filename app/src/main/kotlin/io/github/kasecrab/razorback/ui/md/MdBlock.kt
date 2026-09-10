package io.github.kasecrab.razorback.ui.md

sealed class Inline {
    class Text(val text: String) : Inline()
    class Code(val code: String) : Inline()
    class Strong(val children: List<Inline>) : Inline()
    class Em(val children: List<Inline>) : Inline()
    class Strike(val children: List<Inline>) : Inline()
    class Link(val children: List<Inline>, val url: String) : Inline()
    class Image(val alt: String, val url: String) : Inline()
    object SoftBreak : Inline()
    object HardBreak : Inline()
}

enum class Align { LEFT, CENTER, RIGHT }

class ListItem(val blocks: List<MdBlock>)

sealed class MdBlock {
    class Paragraph(val inlines: List<Inline>) : MdBlock()
    class Heading(val level: Int, val inlines: List<Inline>) : MdBlock()
    class Fence(val lang: String?, val code: String, val closed: Boolean) : MdBlock()
    class ListBlock(val ordered: Boolean, val start: Int, val items: List<ListItem>) : MdBlock()
    class Quote(val blocks: List<MdBlock>) : MdBlock()
    class Table(val header: List<List<Inline>>, val aligns: List<Align>, val rows: List<List<List<Inline>>>) : MdBlock()
    class Image(val alt: String, val url: String) : MdBlock()
    object Rule : MdBlock()
}

/** A top-level block with the exact source it was parsed from, so unchanged blocks can be skipped. */
class TopBlock(val block: MdBlock, val source: String)
