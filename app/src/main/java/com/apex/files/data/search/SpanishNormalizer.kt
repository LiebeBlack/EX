package com.apex.files.data.search

import java.text.Normalizer

/**
 * Spanish-aware text normalization. Pure JVM, unit-testable.
 *
 * The content index stores text already normalized (lowercase, accents
 * stripped) so queries and stored text compare directly; [tokens] also drops
 * common stopwords for BM25 term extraction.
 */
object SpanishNormalizer {

    private val STOPWORDS = setOf(
        "de", "la", "el", "los", "las", "un", "una", "unos", "unas", "y", "o", "u",
        "a", "al", "del", "en", "por", "para", "con", "sin", "sobre", "entre",
        "hasta", "desde", "que", "se", "su", "sus", "mi", "mis", "tu", "tus",
        "es", "son", "fue", "era", "ser", "ha", "han", "he", "hay", "esta",
        "este", "estos", "estas", "estan", "no", "si", "me", "te", "le", "lo",
        "les", "muy", "mas", "menos", "tambien", "como", "cuando", "donde",
        "cual", "cuales", "quien", "quienes", "todo", "toda", "todos", "todas",
        "otro", "otra", "otros", "otras", "ese", "esa", "esos", "esas",
        "aquel", "aquella", "aquellos", "aquellas", "esto", "eso", "aquello",
        "the", "and", "of", "to", "in", "for", "with", "on", "at", "from",
        "by", "is", "are", "was", "were", "an", "it", "its", "this", "that",
        "these", "those",
    )

    /**
     * Lowercases and strips diacritics (NFD decomposition). Keeps every other
     * character untouched, so the result can be searched with plain contains.
     */
    fun normalize(text: String): String {
        if (text.isEmpty()) return ""
        val decomposed = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        val sb = StringBuilder(decomposed.length)
        for (c in decomposed) {
            if (c.code < 0x0300 || c.code > 0x036F) sb.append(c)
        }
        return sb.toString()
    }

    /** Word tokens of [text] (normalized, stopwords removed, length > 1). */
    fun tokens(text: String): List<String> {
        val normalized = normalize(text)
        if (normalized.isEmpty()) return emptyList()
        val out = ArrayList<String>(normalized.length / 6 + 1)
        val sb = StringBuilder()
        for (c in normalized) {
            if (c.isLetterOrDigit()) {
                sb.append(c)
            } else if (sb.isNotEmpty()) {
                addToken(sb, out)
            }
        }
        if (sb.isNotEmpty()) addToken(sb, out)
        return out
    }

    private fun addToken(sb: StringBuilder, out: MutableList<String>) {
        val word = sb.toString()
        sb.setLength(0)
        if (word.length > 1 && word !in STOPWORDS) out.add(word)
    }
}