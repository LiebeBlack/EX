package com.apex.files

import com.apex.files.data.fs.StructuredFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredFormatTest {

    @Test
    fun `detects json from braces`() {
        assertEquals(StructuredFormat.Kind.JSON, StructuredFormat.detect("  {\"a\":1}"))
        assertEquals(StructuredFormat.Kind.JSON, StructuredFormat.detect("[1,2]"))
    }

    @Test
    fun `detects xml from angle bracket`() {
        assertEquals(StructuredFormat.Kind.XML, StructuredFormat.detect("<?xml version=\"1.0\"?><a/>"))
        assertEquals(StructuredFormat.Kind.XML, StructuredFormat.detect("<root></root>"))
    }

    @Test
    fun `blank or plain text is not structured`() {
        assertNull(StructuredFormat.detect("   "))
        assertNull(StructuredFormat.detect("hello world"))
        assertNull(StructuredFormat.detect("123"))
    }

    @Test
    fun `pretty json indents objects and arrays`() {
        val pretty = StructuredFormat.format("""{"b":2,"a":{"c":[1,2,{"d":true}]},"e":null}""", StructuredFormat.Kind.JSON)
        assertNotNull(pretty)
        val p = pretty!!
        assertTrue(p.startsWith("{\n"))
        assertTrue(p.endsWith("\n}"))
        assertTrue(p.contains("\n  \"b\": 2,\n"))
        assertTrue(p.contains("\n    \"c\": [\n"))
        assertTrue(p.contains("\n      true\n"))
    }

    @Test
    fun `json preserves string escapes`() {
        val pretty = StructuredFormat.format("""{"path":"a\\nb","q":"say \"hi\""}""", StructuredFormat.Kind.JSON)
        assertNotNull(pretty)
        assertTrue(pretty!!.contains("\"a\\\\nb\""))
    }

    @Test
    fun `json with trailing comma is rejected`() {
        assertNull(StructuredFormat.format("""{"a":1,}""", StructuredFormat.Kind.JSON))
    }

    @Test
    fun `json rejects trailing garbage`() {
        assertNull(StructuredFormat.format("""{"a":1} x""", StructuredFormat.Kind.JSON))
    }

    @Test
    fun `pretty xml indents nested elements`() {
        val pretty = StructuredFormat.format(
            "<root a=\"1\"><child><leaf>text</leaf></child><empty/></root>",
            StructuredFormat.Kind.XML,
        )
        assertNotNull(pretty)
        val p = pretty!!
        assertTrue(p.startsWith("<root a=\"1\">\n"))
        assertTrue(p.contains("\n  <child>\n"))
        assertTrue(p.contains("\n    <leaf>"))
        assertTrue(p.contains("text"))
        assertTrue(p.endsWith("\n</root>"))
    }

    @Test
    fun `pretty xml keeps comments and processing instructions`() {
        val pretty = StructuredFormat.format(
            "<?xml version=\"1.0\"?><r><!-- note --><a/></r>",
            StructuredFormat.Kind.XML,
        )
        assertNotNull(pretty)
        assertTrue(pretty!!.contains("<?xml version=\"1.0\"?>"))
        assertTrue(pretty.contains("<!-- note -->"))
    }

    @Test
    fun `pretty xml rejects unclosed tags`() {
        assertNull(StructuredFormat.format("<a><b></a>", StructuredFormat.Kind.XML))
        assertNull(StructuredFormat.format("<a><b></b>", StructuredFormat.Kind.XML))
    }

    @Test
    fun `pretty xml rejects text outside root`() {
        assertNull(StructuredFormat.format("stray<a/>", StructuredFormat.Kind.XML))
    }
}
