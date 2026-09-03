package com.apex.files

import com.apex.files.data.fs.CategoryEngine
import com.apex.files.data.model.Category
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryEngineTest {

    @Test
    fun `classifies common extensions`() {
        assertEquals(Category.IMAGE, CategoryEngine.classify("foto.png"))
        assertEquals(Category.IMAGE, CategoryEngine.classify("photo.JPG"))
        assertEquals(Category.IMAGE, CategoryEngine.classify("imagen.webp"))
        assertEquals(Category.VIDEO, CategoryEngine.classify("video.mp4"))
        assertEquals(Category.VIDEO, CategoryEngine.classify("clip.MKV"))
        assertEquals(Category.AUDIO, CategoryEngine.classify("cancion.flac"))
        assertEquals(Category.AUDIO, CategoryEngine.classify("track.mp3"))
        assertEquals(Category.DOCUMENT, CategoryEngine.classify("informe.pdf"))
        assertEquals(Category.DOCUMENT, CategoryEngine.classify("notas.txt"))
        assertEquals(Category.APK, CategoryEngine.classify("app.apk"))
        assertEquals(Category.APK, CategoryEngine.classify("bundle.XAPK"))
    }

    @Test
    fun `classifies compound archives before simple ones`() {
        assertEquals(Category.ARCHIVE, CategoryEngine.classify("backup.tar.gz"))
        assertEquals(Category.ARCHIVE, CategoryEngine.classify("backup.tgz"))
        assertEquals(Category.ARCHIVE, CategoryEngine.classify("datos.zip"))
        assertEquals(Category.ARCHIVE, CategoryEngine.classify("pelicula.rar"))
    }

    @Test
    fun `unknown extensions fall back to OTHER`() {
        assertEquals(Category.OTHER, CategoryEngine.classify("weird.xyz"))
        assertEquals(Category.OTHER, CategoryEngine.classify("sin_extension"))
    }

    @Test
    fun `extension extraction is lowercase and dot-free`() {
        assertEquals("png", CategoryEngine.extensionOf("foto.PNG"))
        assertEquals("gz", CategoryEngine.extensionOf("a.tar.gz"))
        assertEquals("", CategoryEngine.extensionOf("notas"))
        assertEquals("", CategoryEngine.extensionOf(".gitignore"))
    }
}