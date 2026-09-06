package com.apex.files

import com.apex.files.data.search.SmartClassifier
import com.apex.files.data.search.SmartGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmartClassifierTest {

    @Test
    fun `receipt keywords in the name`() {
        assertEquals(SmartGroup.COMPROBANTE, SmartClassifier.classify("factura_enero.pdf", ""))
        assertEquals(SmartGroup.COMPROBANTE, SmartClassifier.classify("recibo_2026.jpg", ""))
        assertEquals(SmartGroup.COMPROBANTE, SmartClassifier.classify("boleta de compra.png", ""))
    }

    @Test
    fun `receipt keywords in the content text`() {
        assertEquals(
            SmartGroup.COMPROBANTE,
            SmartClassifier.classify("scan_01.pdf", "factura total iva 21%"),
        )
        assertEquals(
            SmartGroup.COMPROBANTE,
            SmartClassifier.classify("IMG_0001.jpg", "recibo de pago abono"),
        )
    }

    @Test
    fun `work documents`() {
        assertEquals(SmartGroup.TRABAJO, SmartClassifier.classify("cv_juan_perez.docx", ""))
        assertEquals(SmartGroup.TRABAJO, SmartClassifier.classify("informe_q3.txt", ""))
        assertEquals(
            SmartGroup.TRABAJO,
            SmartClassifier.classify("documento.docx", "contrato de trabajo firmado"),
        )
    }

    @Test
    fun `code, temp and apk take priority over documents`() {
        assertEquals(SmartGroup.CODIGO, SmartClassifier.classify("main.kt", "factura")) // code wins over receipt text
        assertEquals(SmartGroup.CODIGO, SmartClassifier.classify("app.js", ""))
        assertEquals(SmartGroup.TEMPORAL, SmartClassifier.classify("descarga.tmp", ""))
        assertEquals(SmartGroup.TEMPORAL, SmartClassifier.classify("~backup.xlsx", ""))
        assertEquals(SmartGroup.APK, SmartClassifier.classify("app-v2.apk", ""))
    }

    @Test
    fun `multimedia falls through`() {
        assertEquals(SmartGroup.MULTIMEDIA, SmartClassifier.classify("foto_playa.jpg", ""))
        assertEquals(SmartGroup.MULTIMEDIA, SmartClassifier.classify("video.mp4", ""))
        assertEquals(SmartGroup.MULTIMEDIA, SmartClassifier.classify("cancion.mp3", ""))
    }

    @Test
    fun `unclassifiable files are null`() {
        assertNull(SmartClassifier.classify("notas.txt", ""))
        assertNull(SmartClassifier.classify("datos.bin", ""))
        assertNull(SmartClassifier.classify("carpeta", ""))
    }

    @Test
    fun `short receipt words need word boundaries`() {
        // "iva" must not match inside "viva" or "grativa".
        assertNull(SmartClassifier.classify("viva_mexico.txt", ""))
        assertEquals(
            SmartGroup.COMPROBANTE,
            SmartClassifier.classify("documento.pdf", "pagado con iva incluido"),
        )
    }
}