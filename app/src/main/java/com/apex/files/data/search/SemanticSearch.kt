package com.apex.files.data.search

import com.apex.files.data.fs.MemoryIndex
import com.apex.files.data.fs.SearchFilters
import com.apex.files.data.model.Category
import com.apex.files.data.model.FileNode

/**
 * Orchestrates the semantic search: hard filters (size/date/ext/category/smart
 * group) -> cheap contains pre-filter (normalized name + indexed content text)
 * -> BM25 ranking -> classic fallback. Pure JVM; [textOf] supplies indexed
 * content text (see [com.apex.files.data.fs.ContentIndex]).
 */
object SemanticSearch {

    /** Upper bound of candidates scanned per query (RAM/time guard). */
    private const val CANDIDATE_CAP = 60_000

    /**
     * @param textOf content text provider by path ("" when not indexed)
     */
    fun search(
        index: MemoryIndex,
        textOf: (String) -> String,
        query: String,
        sizeBand: SearchFilters.SizeBand? = null,
        dateRange: SearchFilters.DateRange? = null,
        extFilter: String? = null,
        category: Category? = null,
        smartGroup: SmartGroup? = null,
        limit: Int = 400,
    ): List<FileNode> {
        // Implicit filters from the phrasing ("del mes pasado", "grandes").
        val parsed = RelativeDateParser.parse(query)
        val effectiveSize = sizeBand ?: parsed.sizeBand
        val effectiveDate = dateRange ?: parsed.dateRange
        val cleanedQuery = parsed.query.trim()

        var candidates = index.search(
            query = "",
            sizeBand = effectiveSize,
            dateRange = effectiveDate,
            extFilter = extFilter,
            category = category,
            limit = CANDIDATE_CAP,
        )
        if (smartGroup != null) {
            candidates = candidates.filter { SmartClassifier.classify(it, textOf(it.path)) == smartGroup }
        }

        val terms = SynonymExpander.expand(SpanishNormalizer.tokens(cleanedQuery))
        if (terms.isEmpty()) {
            // Only filter words (dates/sizes) or a blank query: plain listing,
            // name-prefix first, mirroring the classic index behavior.
            val sorted = candidates.sortedBy { it.name.lowercase() }
            return sorted.take(limit)
        }

        // Cheap pre-filter: name or indexed text must contain at least one
        // expanded term (both sides normalized). BM25 then ranks the survivors.
        val pre = candidates.filter { node ->
            val name = SpanishNormalizer.normalize(node.name)
            val text = textOf(node.path)
            terms.keys.any { t -> name.contains(t) || text.contains(t) }
        }
        if (pre.isEmpty()) return emptyList()

        return Bm25Ranker.score(pre, textOf, terms, limit)
    }
}