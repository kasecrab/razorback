package io.github.kasecrab.razorback.core

/**
 * Server-sent events, one line at a time. Comment lines (": OPENROUTER PROCESSING")
 * are dropped, several data lines in one event are joined with newlines, and the
 * "[DONE]" sentinel is reported as [Item.Done].
 */
class Sse {

    sealed class Item {
        class Data(val text: String) : Item()
        object Done : Item()
    }

    private val data = StringBuilder(256)
    private var hasData = false

    /** Feed a line without its trailing newline; a trailing CR is tolerated. */
    fun push(raw: String): Item? {
        val line = if (raw.endsWith('\r')) raw.substring(0, raw.length - 1) else raw
        if (line.isEmpty()) return flush()
        if (line[0] == ':') return null
        val colon = line.indexOf(':')
        val field = if (colon < 0) line else line.substring(0, colon)
        var value = if (colon < 0) "" else line.substring(colon + 1)
        if (value.startsWith(" ")) value = value.substring(1)
        if (field == "data") {
            if (hasData) data.append('\n')
            data.append(value)
            hasData = true
        }
        return null
    }

    /** Emit whatever is pending, for the end of the stream. */
    fun flush(): Item? {
        if (!hasData) return null
        val text = data.toString()
        data.setLength(0)
        hasData = false
        return if (text == "[DONE]") Item.Done else Item.Data(text)
    }
}
