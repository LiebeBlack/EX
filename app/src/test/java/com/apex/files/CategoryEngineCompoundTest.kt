package com.apex.files

import com.apex.files.data.fs.CategoryEngine
import com.apex.files.data.model.Category
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryEngineCompoundTest {

    @Test
    fun `compound archives are classified as archives`() {
        assertEquals(Category.ARCHIVE, CategoryEngine.classify("fondo.tar.gz"))
        assertEquals(Category.ARCHIVE, CategoryEngine.classify("imagenes.tar.bz2"))
        assertEquals(Category.ARCHIVE, CategoryEngine.classify("copia.tar.xz"))
        assertEquals(Category.ARCHIVE, CategoryEngine.classify("fotos.tar.zst"))
    }

    @Test
    fun `extension extraction returns the final extension`() {
        // Classification handles compound archives; the plain extension is
        // the segment after the last dot (gz, not tar.gz).
        assertEquals("gz", CategoryEngine.extensionOf("fondo.tar.gz"))
        assertEquals("zst", CategoryEngine.extensionOf("fotos.tar.zst"))
        assertEquals("pdf", CategoryEngine.extensionOf("doc.PDF"))
        assertEquals("", CategoryEngine.extensionOf("sin_extension"))
        assertEquals("", CategoryEngine.extensionOf("archivo."))
    }

    @Test
    fun `case insensitive media classification`() {
        assertEquals(Category.IMAGE, CategoryEngine.classify("FOTO.JPG"))
        assertEquals(Category.VIDEO, CategoryEngine.classify("clip.MKV"))
        assertEquals(Category.AUDIO, CategoryEngine.classify("pista.FLAC"))
        assertEquals(Category.APK, CategoryEngine.classify("app.APKS"))
        assertEquals(Category.DOCUMENT, CategoryEngine.classify("notas.MD"))
    }
}
