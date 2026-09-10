package io.github.kasecrab.razorback.voice

/** Markdown is for eyes. This strips it down to what a voice should actually say. */
object SpeechText {

    private val FENCE = Regex("```[\\s\\S]*?(```|$)")
    private val TABLE_ROW = Regex("(?m)^[ \\t]*\\|.*\\|[ \\t]*$")
    private val HEADING = Regex("(?m)^[ \\t]{0,3}#{1,6}[ \\t]+")
    private val BULLET = Regex("(?m)^[ \\t]*(?:[-*+]|\\d+[.)])[ \\t]+")
    private val QUOTE = Regex("(?m)^[ \\t]*>[ \\t]?")
    private val IMAGE = Regex("!\\[([^\\]]*)]\\([^)]*\\)")
    private val LINK = Regex("\\[([^\\]]+)]\\([^)]*\\)")
    private val URL = Regex("https?://\\S+")
    private val INLINE_CODE = Regex("`([^`]*)`")
    private val EMPHASIS = Regex("(\\*\\*|__|\\*|_|~~)(?=\\S)(.+?)(?<=\\S)\\1")
    private val RULE = Regex("(?m)^[ \\t]*([-*_])([ \\t]*\\1){2,}[ \\t]*$")
    private val SPACES = Regex("[ \\t]{2,}")
    private val BLANKS = Regex("\\n{3,}")

    fun strip(md: String): String {
        var s = md
        s = FENCE.replace(s, "Code omitted.")
        s = TABLE_ROW.replace(s, "")
        s = IMAGE.replace(s) { it.groupValues[1].ifBlank { "image" } }
        s = LINK.replace(s) { it.groupValues[1] }
        s = URL.replace(s, "link")
        s = HEADING.replace(s, "")
        s = QUOTE.replace(s, "")
        s = BULLET.replace(s, "")
        s = RULE.replace(s, "")
        s = INLINE_CODE.replace(s) { it.groupValues[1] }
        var prev: String
        do {
            prev = s
            s = EMPHASIS.replace(s) { it.groupValues[2] }
        } while (s != prev)
        s = SPACES.replace(s, " ")
        s = BLANKS.replace(s, "\n\n")
        return s.trim()
    }
}
