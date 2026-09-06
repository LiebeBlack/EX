package com.apex.files.data.search

import com.apex.files.data.fs.SearchFilters

/**
 * Extracts implicit filters from natural-language queries: "facturas del mes
 * pasado" -> DateRange.MONTH, "archivos grandes" -> SizeBand.GIANT. The
 * recognized phrases are removed from the query so the rest can be ranked.
 * Pure JVM.
 */
object RelativeDateParser {

    data class Parsed(
        val query: String,
        val dateRange: SearchFilters.DateRange? = null,
        val sizeBand: SearchFilters.SizeBand? = null,
    )

    // Applied in order; longer phrases first so "mes pasado" wins over "este mes".
    private val DATE_PHRASES: List<Pair<List<String>, SearchFilters.DateRange>> = listOf(
        listOf("mes pasado", "el mes pasado", "ultimo mes", "el ultimo mes") to SearchFilters.DateRange.MONTH,
        listOf("semana pasada", "la semana pasada", "ultima semana", "la ultima semana") to SearchFilters.DateRange.WEEK,
        listOf("anio pasado", "el anio pasado", "ultimo anio", "el ultimo anio") to SearchFilters.DateRange.YEAR,
        listOf("ayer", "de ayer", "el dia de ayer") to SearchFilters.DateRange.TODAY,
        listOf("hoy", "de hoy", "este dia") to SearchFilters.DateRange.TODAY,
        listOf("este mes") to SearchFilters.DateRange.MONTH,
        listOf("esta semana") to SearchFilters.DateRange.WEEK,
        listOf("este anio") to SearchFilters.DateRange.YEAR,
    )

    private val SIZE_PHRASES: List<Pair<List<String>, SearchFilters.SizeBand>> = listOf(
        listOf("grandes", "gigantes", "pesados", "enormes", "gordos", "muy grandes") to SearchFilters.SizeBand.GIANT,
        listOf("pequenos", "ligeros", "livianos", "chicos", "muy pequenos") to SearchFilters.SizeBand.SMALL,
        listOf("medianos", "medianas") to SearchFilters.SizeBand.MEDIUM,
    )

    fun parse(raw: String): Parsed {
        val tokens = raw.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        var dateRange: SearchFilters.DateRange? = null
        var sizeBand: SearchFilters.SizeBand? = null
        var out = tokens
        for ((phrases, range) in DATE_PHRASES) {
            val match = findPhrase(out, phrases) ?: continue
            out = removeWithPrecedingConnectors(out, match)
            dateRange = range
            break
        }
        for ((phrases, band) in SIZE_PHRASES) {
            val match = findPhrase(out, phrases) ?: continue
            out = removeWithPrecedingConnectors(out, match)
            sizeBand = band
            break
        }
        return Parsed(out.joinToString(" "), dateRange, sizeBand)
    }

    /** Function words that link a noun to its modifier phrase ("facturas DEL
     *  mes pasado"); dropped together with the matched phrase. */
    private val CONNECTORS = setOf("de", "del", "la", "el", "los", "las", "al", "a", "un", "una", "muy")

    private data class PhraseMatch(val start: Int, val endExclusive: Int)

    private fun findPhrase(tokens: List<String>, phrases: List<String>): PhraseMatch? {
        for (phrase in phrases) {
            val words = phrase.split(' ')
            if (words.size > tokens.size) continue
            outer@ for (i in 0..tokens.size - words.size) {
                for (j in words.indices) {
                    if (tokens[i + j] != words[j]) continue@outer
                }
                return PhraseMatch(i, i + words.size)
            }
        }
        return null
    }

    /** Removes the matched phrase and any connector words hanging right
     *  before it ("facturas del mes pasado" -> "facturas"). */
    private fun removeWithPrecedingConnectors(tokens: List<String>, match: PhraseMatch): List<String> {
        var start = match.start
        while (start > 0 && tokens[start - 1] in CONNECTORS) start--
        return tokens.subList(0, start) + tokens.subList(match.endExclusive, tokens.size)
    }
}