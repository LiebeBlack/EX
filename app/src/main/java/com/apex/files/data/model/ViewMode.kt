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