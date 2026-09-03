package com.apex.files.data.fs

import com.apex.files.data.model.Category

/**
 * Regex-based categorization engine. All patterns are compiled once at
 * class-load time; classifying a name is a handful of short regex matches
 * (microseconds). Pure JVM, unit-testable.
 */
object CategoryEngine {

    private val IMAGE = Regex("""\.(png|jpe?g|webp|gif|bmp|heic|heif|avif|svg|ico|tiff?)$""", RegexOption.IGNORE_CASE)
    private val VIDEO = Regex("""\.(mp4|mkv|webm|avi|mov|3gp|3g2|m4v|wmv|flv|ts|m2ts|mts)$""", RegexOption.IGNORE_CASE)
    private val AUDIO = Regex("""\.(mp3|wav|flac|aac|ogg|oga|m4a|opus|wma|midi?|amr|aiff?|ape)$""", RegexOption.IGNORE_CASE)
    private val DOCUMENT = Regex("""\.(pdf|docx?|xlsx?|pptx?|odt|ods|odp|txt|md|rtf|csv|epub|tex|log|json|xml|html?|ya?ml|ini|cfg|conf)$""", RegexOption.IGNORE_CASE)
    private val ARCHIVE = Regex("""\.(zip|rar|7z|tar|gz|tgz|bz2|xz|zst|lz4|jar|war|cbz|cbr)$""", RegexOption.IGNORE_CASE)
    private val APK = Regex("""\.(apk|xapk|apks)$""", RegexOption.IGNORE_CASE)

    /** Compound extensions that must be checked before the simple ones. */
    private val COMPOUND_ARCHIVES = setOf("tar.gz", "tar.bz2", "tar.xz", "tar.zst")

    /** Lower-cased extension without the dot; "" when there is none. */
    fun extensionOf(name: String): String {
        val base = name.substringBeforeLast('/', name)
        val dot = base.lastIndexOf('.')
        if (dot <= 0 || dot == base.length - 1) return ""
        return base.substring(dot + 1).lowercase()
    }

    fun classify(name: String): Category {
        val lower = name.lowercase()
        if (COMPOUND_ARCHIVES.any { lower.endsWith(".$it") }) return Category.ARCHIVE
        return when {
            APK.containsMatchIn(lower) -> Category.APK
            IMAGE.containsMatchIn(lower) -> Category.IMAGE
            VIDEO.containsMatchIn(lower) -> Category.VIDEO
            AUDIO.containsMatchIn(lower) -> Category.AUDIO
            ARCHIVE.containsMatchIn(lower) -> Category.ARCHIVE
            DOCUMENT.containsMatchIn(lower) -> Category.DOCUMENT
            else -> Category.OTHER
        }
    }
}