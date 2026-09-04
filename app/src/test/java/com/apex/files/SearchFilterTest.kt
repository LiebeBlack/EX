package com.apex.files

import com.apex.files.data.fs.SearchFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFilterTest {

    private val MB = 1024L * 1024
    private val GB = 1024L * MB
    private val hour = 3600_000L
    private val day = 24L * hour
    private val now = 1_718_452_800_000L

    @Test
    fun `size bands`() {
        assertTrue(SearchFilters.matchesSize(500 * 1024, SearchFilters.SizeBand.SMALL))
        assertFalse(SearchFilters.matchesSize(MB, SearchFilters.SizeBand.SMALL))
        assertTrue(SearchFilters.matchesSize(50 * MB, SearchFilters.SizeBand.MEDIUM))
        assertFalse(SearchFilters.matchesSize(GB, SearchFilters.SizeBand.MEDIUM))
        assertTrue(SearchFilters.matchesSize(2 * GB, SearchFilters.SizeBand.GIANT))
        assertFalse(SearchFilters.matchesSize(500 * MB, SearchFilters.SizeBand.GIANT))
    }

    @Test
    fun `date ranges`() {
        assertTrue(SearchFilters.matchesDate(now - hour, SearchFilters.DateRange.TODAY, now))
        assertFalse(SearchFilters.matchesDate(now - 2 * day, SearchFilters.DateRange.TODAY, now))
        assertTrue(SearchFilters.matchesDate(now - 3 * day, SearchFilters.DateRange.WEEK, now))
        assertFalse(SearchFilters.matchesDate(now - 10 * day, SearchFilters.DateRange.WEEK, now))
        assertTrue(SearchFilters.matchesDate(now - 20 * day, SearchFilters.DateRange.MONTH, now))
        assertTrue(SearchFilters.matchesDate(now - 200 * day, SearchFilters.DateRange.YEAR, now))
        assertFalse(SearchFilters.matchesDate(now - 400 * day, SearchFilters.DateRange.YEAR, now))
        assertFalse(SearchFilters.matchesDate(0L, SearchFilters.DateRange.TODAY, now))
    }

    @Test
    fun `name matcher for the in-folder live filter`() {
        assertTrue(SearchFilters.matchesName("Vacaciones.jpg", "vaca"))
        assertTrue(SearchFilters.matchesName("Vacaciones.jpg", "VACA"))
        assertTrue(SearchFilters.matchesName("Vacaciones.jpg", "es.JPG"))
        assertFalse(SearchFilters.matchesName("Vacaciones.jpg", "playa"))
        assertTrue(SearchFilters.matchesName("Cualquier cosa", "  "))
        assertTrue(SearchFilters.matchesName("Cualquier cosa", ""))
    }

    @Test
    fun `extension wildcards`() {
        assertTrue(SearchFilters.matchesExtension("app.apk", "*.apk"))
        assertTrue(SearchFilters.matchesExtension("App.APK", "*.apk"))
        assertTrue(SearchFilters.matchesExtension("informe.pdf", "pdf"))
        assertTrue(SearchFilters.matchesExtension("foto.PNG", "*.png"))
        assertFalse(SearchFilters.matchesExtension("notas.txt", "*.apk"))
        assertFalse(SearchFilters.matchesExtension("a.apk.txt", "*.apk"))
        assertFalse(SearchFilters.matchesExtension("", "*.apk"))
    }
}