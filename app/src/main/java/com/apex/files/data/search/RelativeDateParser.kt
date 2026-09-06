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
        var q = " ${raw.lowercase()} ".replace(Regex("\\s+"), " ")
        var dateRange: SearchFilters.DateRange? = null
        for ((phrases, range) in DATE_PHRASES) {
            val matched = phrases.firstOrNull { q.contains(it) } ?: continue
            q = q.replace(matched, " ")
            dateRange = range
            break
        }
        var sizeBand: SearchFilters.SizeBand? = null
        for ((phrases, band) in SIZE_PHRASES) {
            val matched = phrases.firstOrNull { q.contains(it) } ?: continue
            q = q.replace(matched, " ")
            sizeBand = band
            break
        }
        return Parsed(q.trim(), dateRange, sizeBand)
    }
}