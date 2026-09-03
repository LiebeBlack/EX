package com.apex.files.data.model

import android.net.Uri
import java.io.File

/**
 * A browsable root. [Fs] is a real filesystem volume (internal storage or
 * SD card, reachable through All Files Access); [Saf] is a tree granted
 * through ACTION_OPEN_DOCUMENT_TREE (USB-OTG or SAF fallback).
 */
sealed class Location {

    abstract val label: String

    data class Fs(val root: File) : Location() {
        override val label: String get() = root.name.ifBlank { "Almacenamiento" }
    }

    data class Saf(val rootUri: Uri, val rootName: String) : Location() {
        override val label: String get() = rootName
    }

    /** Stable identity used as a ViewModel key. */
    fun key(): String = when (this) {
        is Fs -> "fs:${root.absolutePath}"
        is Saf -> "saf:$rootUri"
    }
}