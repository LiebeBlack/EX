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

    fun mimeOf(node: FileNode): String {
        if (node.isDir) return "inode/directory"
        val ext = node.extension
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        return mime ?: "application/octet-stream"
    }
}