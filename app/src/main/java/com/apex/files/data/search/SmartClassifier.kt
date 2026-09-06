package com.apex.files.data.search

import com.apex.files.data.fs.CategoryEngine
import com.apex.files.data.model.FileNode

/** Smart (content-aware) grouping labels used by the dynamic folders. */
enum class SmartGroup(val label: String, val chipLabel: String) {
    COMPROBANTE("Comprobantes de Pago", "Comprobantes"),
    TRABAJO("Documentos de Trabajo", "Trabajo"),
    CODIGO("Código Fuente", "Código"),
    TEMPORAL("Archivos Temporales", "Temporales"),
    APK("APKs", "APKs"),
    MULTIMEDIA("Multimedia", "Multimedia"),
}

/**
 * Classifies files into semantic groups combining extension, file name and
 * indexed content text (OCR / PDF / plain text, already normalized).
 *
 * Rule priority: TEMPORAL > APK > CODIGO > COMPROBANTE > TRABAJO > MULTIMEDIA.
 * Pure JVM, unit-testable.
 */
object SmartClassifier {

    private val CODE_EXTS = setOf(
        "kt", "kts", "java", "py", "js", "ts", "tsx", "jsx", "c", "cpp", "cc", "h", "hpp",
        "cs", "go", "rs", "rb", "php", "swift", "sh", "bat", "ps1", "sql", "html", "htm",
        "css", "scss", "less", "xml", "json", "yml", "yaml", "toml", "gradle", "properties",
        "vue", "svelte", "dart", "lua", "pl", "r", "scala", "groovy", "m", "mm",
    )

    private val TEMP_EXTS = setOf("tmp", "part", "crdownload", "download", "temp", "swp", "swo")

    private val APK_EXTS = setOf("apk", "xapk", "apks")

    private val IMAGE_EXTS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif", "avif", "svg", "ico", "tiff", "tif")
    private val VIDEO_EXTS = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "3g2", "m4v", "wmv", "flv", "ts", "m2ts", "mts")
    private val AUDIO_EXTS = setOf("mp3", "wav", "flac", "aac", "ogg", "oga", "m4a", "opus", "wma", "midi", "mid", "amr", "aiff", "aif", "ape")
    private val DOC_EXTS = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "txt", "md",
        "rtf", "csv", "epub", "tex", "log", "json", "xml", "html", "htm", "yml", "yaml", "ini", "cfg", "conf",
    )

    private val RECEIPT_WORDS = listOf(
        "factura", "recibo", "boleta", "comprobante", "invoice", "receipt", "ticket",
        "pago", "payment", "abono", "iva", "nif", "cif", "cobro", "cobros",
        "domiciliacion", "transferencia", "paypal", "vigencia", "total", "subtotal",
    )

    private val WORK_WORDS = listOf(
        "trabajo", "curriculum", "hoja de vida", "informe", "report", "reporte",
        "contrato", "oficio", "proyecto", "presupuesto", "memo", "propuesta",
        "planilla", "nomina", "contratacion", "entrevista", "oferta laboral", "cv",
    )

    /** Classifies using the file name only (content text empty/unknown). */
    fun classify(name: String): SmartGroup? = classify(name, "")

    /**
     * @param name raw file name
     * @param text content text, expected already normalized by
     *   [SpanishNormalizer.normalize] ("" when not indexed)
     */
    fun classify(name: String, text: String): SmartGroup? {
        val ext = CategoryEngine.extensionOf(name)
        val lowerName = SpanishNormalizer.normalize(name)

        return when {
            ext in TEMP_EXTS || lowerName.startsWith("~") -> SmartGroup.TEMPORAL
            ext in APK_EXTS -> SmartGroup.APK
            ext in CODE_EXTS -> SmartGroup.CODIGO
            (ext in DOC_EXTS || ext in IMAGE_EXTS) && containsAny(lowerName, text, RECEIPT_WORDS) ->
                SmartGroup.COMPROBANTE
            ext in DOC_EXTS && containsAny(lowerName, text, WORK_WORDS) ->
                SmartGroup.TRABAJO
            ext in IMAGE_EXTS || ext in VIDEO_EXTS || ext in AUDIO_EXTS -> SmartGroup.MULTIMEDIA
            else -> null
        }
    }

    /** Convenience overload over an indexed [FileNode]. */
    fun classify(node: FileNode, text: String): SmartGroup? = classify(node.name, text)

    private val SPLIT = Regex("[^a-z0-9]+")

    private fun containsAny(name: String, text: String, words: List<String>): Boolean {
        var nameSet: Set<String>? = null
        var textSet: Set<String>? = null
        for (word in words) {
            val normalized = SpanishNormalizer.normalize(word)
            if (normalized.length <= 3) {
                // Short tokens need word boundaries ("iva" must not match inside a word).
                if (nameSet == null) nameSet = name.split(SPLIT).toHashSet()
                if (textSet == null) textSet = text.split(SPLIT).toHashSet()
                if (normalized in nameSet!! || normalized in textSet!!) return true
            } else {
                if (name.contains(normalized) || text.contains(normalized)) return true
            }
        }
        return false
    }
}