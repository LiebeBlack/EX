package com.apex.files.data.fs

import android.webkit.MimeTypeMap
import com.apex.files.data.model.FileNode

/** Viewable text/code extensions and MIME resolution. */
object FileKinds {

    val TEXT_EXTS = setOf(
        "txt", "log", "json", "xml", "md", "csv", "ini", "cfg", "conf",
        "yml", "yaml", "html", "htm", "css", "js", "mjs", "ts", "kt", "kts",
        "java", "py", "sh", "bat", "cmd", "c", "h", "cpp", "hpp", "sql",
        "properties", "toml", "gradle", "tsv", "env", "gitignore", "editorconfig",
        "srt", "vtt", "php", "rb", "go", "rs", "swift",
    )

    fun isText(node: FileNode): Boolean = node.extension in TEXT_EXTS

    /**
     * MIME overrides for formats the platform map misses or gets wrong
     * (mkv/webm/flac/opus/heic/…), so external players and viewers always
     * receive a useful type instead of application/octet-stream.
     */
    private val MIME_OVERRIDES: Map<String, String> = mapOf(
        // video
        "mkv" to "video/x-matroska",
        "webm" to "video/webm",
        "avi" to "video/x-msvideo",
        "mov" to "video/quicktime",
        "mpg" to "video/mpeg",
        "mpeg" to "video/mpeg",
        "m2ts" to "video/mp2t",
        "ts" to "video/mp2t",
        "wmv" to "video/x-ms-wmv",
        "flv" to "video/x-flv",
        "ogv" to "video/ogg",
        "3gp" to "video/3gpp",
        "3g2" to "video/3gpp2",
        "m4v" to "video/x-m4v",
        "asf" to "video/x-ms-asf",
        "f4v" to "video/x-f4v",
        // audio
        "flac" to "audio/flac",
        "opus" to "audio/opus",
        "ogg" to "audio/ogg",
        "oga" to "audio/ogg",
        "m4a" to "audio/mp4",
        "m4b" to "audio/mp4",
        "aac" to "audio/aac",
        "amr" to "audio/amr",
        "mka" to "audio/x-matroska",
        "wma" to "audio/x-ms-wma",
        "mid" to "audio/midi",
        "midi" to "audio/midi",
        "3ga" to "audio/3gpp",
        // image / other
        "heic" to "image/heic",
        "heif" to "image/heif",
        "m3u8" to "application/x-mpegURL",
    )

    fun mimeOf(node: FileNode): String {
        if (node.isDir) return "inode/directory"
        val ext = node.extension
        MIME_OVERRIDES[ext]?.let { return it }
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        return mime ?: "application/octet-stream"
    }
}