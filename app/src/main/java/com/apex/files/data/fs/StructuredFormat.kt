package com.apex.files.data.fs

/**
 * Zero-dependency pretty-printers for JSON and XML. Pure functions: a null
 * result means the input could not be parsed, never a partial re-format.
 */
object StructuredFormat {

    enum class Kind { JSON, XML }

    /** Detects the format from the first non-whitespace character. */
    fun detect(text: String): Kind? {
        var i = 0
        while (i < text.length && text[i].isWhitespace()) i++
        return when (text.getOrNull(i)) {
            '{', '[' -> Kind.JSON
            '<' -> Kind.XML
            else -> null
        }
    }

    fun format(text: String, kind: Kind): String? = when (kind) {
        Kind.JSON -> prettyJson(text)
        Kind.XML -> prettyXml(text)
    }

    // --------------------------------------------------------------- JSON

    private class Pos(var i: Int)

    private fun prettyJson(input: String): String? {
        val pos = Pos(0)
        val out = StringBuilder(input.length + 64)
        skipWs(input, pos)
        if (!jsonValue(input, pos, out, 0)) return null
        skipWs(input, pos)
        return if (pos.i == input.length) out.toString() else null
    }

    private fun skipWs(s: String, p: Pos) {
        while (p.i < s.length && s[p.i].isWhitespace()) p.i++
    }

    private fun jsonValue(s: String, p: Pos, out: StringBuilder, depth: Int): Boolean = when (s.getOrNull(p.i)) {
        '{' -> jsonObject(s, p, out, depth)
        '[' -> jsonArray(s, p, out, depth)
        '"' -> {
            val token = stringToken(s, p) ?: return false
            out.append(token)
            true
        }
        else -> {
            val token = primitiveToken(s, p) ?: return false
            out.append(token)
            true
        }
    }

    /** Copies a complete JSON string literal (including quotes). */
    private fun stringToken(s: String, p: Pos): String? {
        if (s.getOrNull(p.i) != '"') return null
        var j = p.i + 1
        while (j < s.length) {
            when (s[j]) {
                '\\' -> j += 2
                '"' -> {
                    val token = s.substring(p.i, j + 1)
                    p.i = j + 1
                    return token
                }
                '\n', '\r' -> return null // raw newline inside a string is invalid JSON
                else -> j++
            }
        }
        return null
    }

    private fun primitiveToken(s: String, p: Pos): String? {
        val start = p.i
        var j = start
        while (j < s.length && s[j] != ',' && s[j] != '}' && s[j] != ']' && !s[j].isWhitespace()) j++
        if (j == start) return null
        val token = s.substring(start, j)
        p.i = j
        return when (token) {
            "true", "false", "null" -> token
            else -> if (token.toDoubleOrNull() != null) token else null
        }
    }

    private fun jsonObject(s: String, p: Pos, out: StringBuilder, depth: Int): Boolean {
        out.append('{')
        p.i++
        skipWs(s, p)
        if (s.getOrNull(p.i) == '}') {
            out.append('}')
            p.i++
            return true
        }
        while (true) {
            skipWs(s, p)
            val key = stringToken(s, p) ?: return false
            skipWs(s, p)
            if (s.getOrNull(p.i) != ':') return false
            p.i++
            skipWs(s, p)
            newlineIndent(out, depth + 1)
            out.append(key).append(": ")
            if (!jsonValue(s, p, out, depth + 1)) return false
            skipWs(s, p)
            when (s.getOrNull(p.i)) {
                ',' -> {
                    p.i++
                    continue
                }
                '}' -> {
                    out.append('\n')
                    indent(out, depth)
                    out.append('}')
                    p.i++
                    return true
                }
                else -> return false
            }
        }
    }

    private fun jsonArray(s: String, p: Pos, out: StringBuilder, depth: Int): Boolean {
        out.append('[')
        p.i++
        skipWs(s, p)
        if (s.getOrNull(p.i) == ']') {
            out.append(']')
            p.i++
            return true
        }
        while (true) {
            skipWs(s, p)
            newlineIndent(out, depth + 1)
            if (!jsonValue(s, p, out, depth + 1)) return false
            skipWs(s, p)
            when (s.getOrNull(p.i)) {
                ',' -> {
                    p.i++
                    continue
                }
                ']' -> {
                    out.append('\n')
                    indent(out, depth)
                    out.append(']')
                    p.i++
                    return true
                }
                else -> return false
            }
        }
    }

    private fun newlineIndent(out: StringBuilder, depth: Int) {
        out.append('\n')
        indent(out, depth)
    }

    private fun indent(out: StringBuilder, depth: Int) {
        repeat(depth) { out.append("  ") }
    }

    // ---------------------------------------------------------------- XML

    private fun prettyXml(input: String): String? {
        if (input.isEmpty()) return null
        val out = StringBuilder(input.length + 32)
        val stack = ArrayList<String>()
        val p = Pos(0)

        fun line() {
            out.append('\n')
            repeat(stack.size) { out.append("  ") }
        }

        while (p.i < input.length) {
            val c = input[p.i]
            if (c != '<') {
                // Text run: up to the next markup start.
                val end = input.indexOf('<', p.i).let { if (it < 0) input.length else it }
                val text = input.substring(p.i, end)
                p.i = end
                val collapsed = text.trim().replace(Regex("\\s+"), " ")
                if (collapsed.isEmpty()) continue
                if (stack.isEmpty()) return null // text outside the root
                line()
                out.append(collapsed)
                continue
            }

            when {
                input.startsWith("<?", p.i) -> {
                    val end = input.indexOf("?>", p.i + 2)
                    if (end < 0) return null
                    line()
                    out.append(input, p.i, end + 2)
                    p.i = end + 2
                }
                input.startsWith("<!--", p.i) -> {
                    val end = input.indexOf("-->", p.i + 4)
                    if (end < 0) return null
                    line()
                    out.append(input, p.i, end + 3)
                    p.i = end + 3
                }
                input.startsWith("<![CDATA[", p.i) -> {
                    val end = input.indexOf("]]>", p.i + 9)
                    if (end < 0) return null
                    line()
                    out.append(input, p.i, end + 3)
                    p.i = end + 3
                }
                input.startsWith("<!", p.i) -> {
                    // DOCTYPE or other declarations; honor quotes and [ ] subsets.
                    var j = p.i + 2
                    var quote = '\u0000'
                    var bracketDepth = 0
                    var closed = false
                    while (j < input.length) {
                        val cj = input[j]
                        if (quote != '\u0000') {
                            if (cj == quote) quote = '\u0000'
                        } else when (cj) {
                            '"', '\'' -> quote = cj
                            '[' -> bracketDepth++
                            ']' -> bracketDepth--
                            '>' -> if (bracketDepth <= 0) {
                                closed = true
                                break
                            }
                        }
                        j++
                    }
                    if (!closed) return null
                    line()
                    out.append(input, p.i, j + 1)
                    p.i = j + 1
                }
                input.startsWith("</", p.i) -> {
                    val end = input.indexOf('>', p.i + 2)
                    if (end < 0) return null
                    val name = input.substring(p.i + 2, end).trim()
                    if (name.isEmpty() || name.any { it.isWhitespace() }) return null
                    if (stack.isEmpty() || stack.last() != name) return null
                    stack.removeAt(stack.lastIndex)
                    line()
                    out.append("</").append(name).append('>')
                    p.i = end + 1
                }
                else -> {
                    // Element open tag: scan to '>' honoring quotes.
                    var j = p.i + 1
                    var quote = '\u0000'
                    var closed = false
                    while (j < input.length) {
                        val cj = input[j]
                        if (quote != '\u0000') {
                            if (cj == quote) quote = '\u0000'
                            j++
                        } else when (cj) {
                            '"', '\'' -> {
                                quote = cj
                                j++
                            }
                            '<' -> return null // nested markup inside a tag
                            '>' -> {
                                closed = true
                                break
                            }
                            else -> j++
                        }
                    }
                    if (!closed) return null
                    val tag = input.substring(p.i, j + 1)
                    p.i = j + 1
                    // Element name = first token after '<'.
                    val nameStart = tag.indexOfFirst { !it.isWhitespace() && it != '<' }
                    if (nameStart < 0) return null
                    val nameBuilder = StringBuilder()
                    var n = nameStart
                    while (n < tag.length) {
                        val ch = tag[n]
                        if (ch.isWhitespace() || ch == '/' || ch == '>') break
                        nameBuilder.append(ch)
                        n++
                    }
                    val name = nameBuilder.toString()
                    if (name.isEmpty() || name.any { it == '<' || it == '>' }) return null
                    val selfClosing = tag.trimEnd().endsWith("/>")
                    line()
                    out.append(tag)
                    if (!selfClosing) {
                        stack.add(name)
                    }
                }
            }
        }
        if (stack.isNotEmpty()) return null // unclosed elements
        return out.toString().ifEmpty { null } // no root content at all
    }
}
