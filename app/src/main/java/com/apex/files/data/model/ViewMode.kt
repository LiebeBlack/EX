package com.apex.files.data.model

/** Explorer rendering mode. Persisted across sessions. */
enum class ViewMode { LIST, GRID;

    companion object {
        fun fromName(name: String?): ViewMode = entries.firstOrNull { it.name == name } ?: LIST
    }
}

/** Explorer sort order. Persisted across sessions. */
enum class SortOrder { NAME, SIZE, DATE;

    companion object {
        fun fromName(name: String?): SortOrder = entries.firstOrNull { it.name == name } ?: NAME
    }
}

/** Direction applied to the active [SortOrder] (directories stay first). */
enum class SortDirection { ASC, DESC;

    companion object {
        fun fromName(name: String?): SortDirection = entries.firstOrNull { it.name == name } ?: ASC
    }
}