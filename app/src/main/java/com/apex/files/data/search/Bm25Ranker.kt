package com.apex.files.data.search

import com.apex.files.data.model.FileNode

/**
 * BM25-style ranking (k1=1.2, b=0.75) over the tokens of
 * name + extension + category label + indexed content text. Pure JVM.
 *
 * The corpus is expected to be the pre-filtered candidate set (docs that
 * contain at least one query term), so idf/avgdl are computed over that set —
 * a good approximation for ranking and far cheaper than a full inverted index.
 */
object Bm25Ranker {

    private const val K1 = 1.2
    private const val B = 0.75

    /**
     * Multiplier applied to docs whose NAME contains a query term. Plain BM25
     * cannot guarantee "name matches outrank content matches": a term shared
     * by many candidate docs has low idf, so a content-heavy doc with several
     * other term hits can outscore the name match. The bonus is applied after
     * scoring, so the BM25 order within each tier is preserved.
     */
    private const val NAME_MATCH_BONUS = 5.0

    /**
     * @param docs candidate nodes (already hard-filtered by size/date/etc.)
     * @param textOf content text provider by path (indexed OCR / PDF / plain
     *   text, normalized; "" when a file is not indexed)
     * @param terms expanded query terms with weights (from [SynonymExpander])
     * @return the top [limit] nodes ordered by descending score
     */
    fun score(
        docs: List<FileNode>,
        textOf: (String) -> String,
        terms: Map<String, Double>,
        limit: Int,
    ): List<FileNode> {
        if (docs.isEmpty() || terms.isEmpty()) return emptyList()

        // Tokenize each doc once: name tokens count double (tf boost) and are
        // kept separately so any name match earns [NAME_MATCH_BONUS]; then
        // extension + category + content.
        data class Doc(val node: FileNode, val tokens: List<String>, val nameTerms: Set<String>)

        val tokenized = ArrayList<Doc>(docs.size)
        var totalTokens = 0L
        for (node in docs) {
            val nameTokens = SpanishNormalizer.tokens(node.name)
            val metaTokens = SpanishNormalizer.tokens("${node.extension} ${node.category.name}")
            val contentTokens = SpanishNormalizer.tokens(textOf(node.path))
            val all = ArrayList<String>(nameTokens.size * 2 + metaTokens.size + contentTokens.size)
            all.addAll(nameTokens)
            all.addAll(nameTokens) // name tf boost
            all.addAll(metaTokens)
            all.addAll(contentTokens)
            tokenized.add(Doc(node, all, nameTokens.toSet()))
            totalTokens += all.size
        }

        val n = tokenized.size
        val avgdl = if (n > 0) totalTokens.toDouble() / n else 1.0

        // Document frequencies over the candidate set.
        val df = HashMap<String, Int>(terms.size * 2)
        for (doc in tokenized) {
            val seen = HashSet<String>(8)
            for (t in doc.tokens) {
                if (t in terms && seen.add(t)) {
                    df[t] = (df[t] ?: 0) + 1
                }
            }
        }

        val scored = ArrayList<Pair<FileNode, Double>>(tokenized.size)
        for (doc in tokenized) {
            val dl = doc.tokens.size.toDouble()
            var score = 0.0
            var nameMatch = false
            for ((term, weight) in terms) {
                val docFreq = df[term] ?: continue
                val idf = Math.log(1.0 + (n - docFreq + 0.5) / (docFreq + 0.5))
                var tf = 0
                for (t in doc.tokens) {
                    if (t == term) tf++
                }
                if (tf == 0) continue
                if (term in doc.nameTerms) nameMatch = true
                val denom = tf + K1 * (1 - B + B * dl / avgdl)
                score += weight * idf * (tf * (K1 + 1)) / denom
            }
            if (score > 0.0) {
                scored.add(doc.node to if (nameMatch) score * NAME_MATCH_BONUS else score)
            }
        }

        scored.sortByDescending { it.second }
        val out = ArrayList<FileNode>(minOf(limit, scored.size))
        for (i in 0 until minOf(limit, scored.size)) out.add(scored[i].first)
        return out
    }
}