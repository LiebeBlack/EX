package com.apex.files.ui.screens.home

/**
 * Home screen sections that can be reordered and toggled.
 */
enum class HomeSection(val id: String, val displayName: String) {
    STORAGE_HERO("storage_hero", "Almacenamiento"),
    QUICK_TOOLS("quick_tools", "Herramientas"),
    SUGGESTIONS("suggestions", "Sugerencias"),
    CATEGORIES("categories", "Categorías"),
    FAVORITES("favorites", "Favoritos"),
    RECENTS("recents", "Recientes"),
    DRIVES("drives", "Unidades");

    companion object {
        fun fromId(id: String): HomeSection? = entries.find { it.id == id }
        
        fun defaultOrder(): List<HomeSection> = listOf(
            STORAGE_HERO,
            QUICK_TOOLS,
            SUGGESTIONS,
            CATEGORIES,
            FAVORITES,
            RECENTS,
            DRIVES
        )
        
        fun parseOrder(orderString: String): List<HomeSection> {
            val sectionIds = orderString.split(",").map { it.trim() }
            val orderedSections = sectionIds.mapNotNull { fromId(it) }
            val remainingSections = entries.filter { it !in orderedSections }
            return orderedSections + remainingSections
        }
        
        fun formatOrder(sections: List<HomeSection>): String {
            return sections.joinToString(",") { it.id }
        }
    }
}

/**
 * Home screen configuration including section order and visibility.
 */
data class HomeConfig(
    val sectionOrder: List<HomeSection>,
    val visibleSections: Set<HomeSection>
) {
    companion object {
        fun fromString(orderString: String, visibilityString: String): HomeConfig {
            val sectionOrder = HomeSection.parseOrder(orderString)
            val visibleIds = visibilityString.split(",").map { it.trim() }.toSet()
            val visibleSections = sectionOrder.filter { it.id in visibleIds }.toSet()
            return HomeConfig(sectionOrder, visibleSections)
        }
        
        fun default(): HomeConfig {
            return HomeConfig(
                sectionOrder = HomeSection.defaultOrder(),
                visibleSections = HomeSection.entries.toSet()
            )
        }
    }
    
    fun isSectionVisible(section: HomeSection): Boolean = section in visibleSections
    
    fun getVisibleSections(): List<HomeSection> = sectionOrder.filter { it in visibleSections }
    
    fun moveSectionUp(section: HomeSection): HomeConfig {
        val currentIndex = sectionOrder.indexOf(section)
        if (currentIndex <= 0) return this
        
        val newOrder = sectionOrder.toMutableList()
        newOrder.removeAt(currentIndex)
        newOrder.add(currentIndex - 1, section)
        
        return copy(sectionOrder = newOrder)
    }
    
    fun moveSectionDown(section: HomeSection): HomeConfig {
        val currentIndex = sectionOrder.indexOf(section)
        if (currentIndex < 0 || currentIndex >= sectionOrder.size - 1) return this
        
        val newOrder = sectionOrder.toMutableList()
        newOrder.removeAt(currentIndex)
        newOrder.add(currentIndex + 1, section)
        
        return copy(sectionOrder = newOrder)
    }
    
    fun toggleSectionVisibility(section: HomeSection): HomeConfig {
        val newVisibleSections = if (section in visibleSections) {
            visibleSections - section
        } else {
            visibleSections + section
        }
        return copy(visibleSections = newVisibleSections)
    }
    
    fun toOrderString(): String = HomeSection.formatOrder(sectionOrder)
    
    fun toVisibilityString(): String = visibleSections.joinToString(",") { it.id }
}