package com.apex.files

import com.apex.files.data.fs.Csv
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvTest {

    @Test
    fun `simple table becomes comma separated rows`() {
        val csv = Csv.toCsv(
            columns = listOf("a", "b"),
            rows = listOf(
                listOf("1", "2"),
                listOf("x", "y"),
            ),
        )
        assertEquals("a,b\r\n1,2\r\nx,y\r\n", csv)
    }

    @Test
    fun `null cells become empty fields`() {
        val csv = Csv.toCsv(listOf("a", "b"), listOf(listOf(null, "v")))
        assertEquals("a,b\r\n,v\r\n", csv)
    }

    @Test
    fun `fields with commas quotes and newlines are escaped`() {
        val csv = Csv.toCsv(
            listOf("a"),
            listOf(
                listOf("he said \"hi\", ok"),
                listOf("line1\nline2"),
            ),
        )
        assertEquals("a\r\n\"he said \"\"hi\"\", ok\"\r\n\"line1\nline2\"\r\n", csv)
    }

    @Test
    fun `empty table only has the header row`() {
        assertEquals("c1,c2\r\n", Csv.toCsv(listOf("c1", "c2"), emptyList()))
    }
}
