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
     * @param docs candidate nodes (already hard-filtered by size/date/etc.)
     * @param textOf content text provider (indexed OCR / PDF / plain text)
     * @param terms expanded query terms with weights (from [SynonymExpander])
     * @return the top [limit] nodes ordered by descending score
     */
    fun score(
        docs: List<FileNode>,
        textOf: (FileNode) -> String,
        terms: Map<String, Double>,
        limit: Int,
    ): List<FileNode> {
        if (docs.isEmpty() || terms.isEmpty()) return emptyList()

        // Tokenize each doc once: name tokens count double (name matches
        // outrank content matches), then extension + category + content.
        data class Doc(val node: FileNode, val tokens: List<String>)

        val tokenized = ArrayList<Doc>(docs.size)
        var totalTokens = 0L
        for (node in docs) {
            val nameTokens = SpanishNormalizer.tokens(node.name)
            val metaTokens = SpanishNormalizer.tokens("${node.extension} ${node.category.name}")
            val contentTokens = SpanishNormalizer.tokens(textOf(node))
            val all = ArrayList<String>(nameTokens.size * 2 + metaTokens.size + contentTokens.size)
            all.addAll(nameTokens)
            all.addAll(nameTokens) // name boost
            all.addAll(metaTokens)
            all.addAll(contentTokens)
            tokenized.add(Doc(node, all))
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
            for ((term, weight) in terms) {
                val docFreq = df[term] ?: continue
                val idf = Math.log(1.0 + (n - docFreq + 0.5) / (docFreq + 0.5))
                var tf = 0
                for (t in doc.tokens) {
                    if (t == term) tf++
                }
                if (tf == 0) continue
                val denom = tf + K1 * (1 - B + B * dl / avgdl)
                score += weight * idf * (tf * (K1 + 1)) / denom
            }
            if (score > 0.0) scored.add(doc.node to score)
        }

        scored.sortByDescending { it.second }
        val out = ArrayList<FileNode>(minOf(limit, scored.size))
        for (i in 0 until minOf(limit, scored.size)) out.add(scored[i].first)
        return out
    }
}