package com.apex.files.data.model

/** Physical categories assigned by the regex-based [CategoryEngine]. */
enum class Category {
    DIRECTORY, IMAGE, VIDEO, AUDIO, DOCUMENT, ARCHIVE, APK, DOWNLOADS, OTHER;

    /** Categories that are shown as Home filters / MediaStore collections. */
    val isMediaCollection: Boolean
        get() = this == IMAGE || this == VIDEO || this == AUDIO || this == DOWNLOADS

    /** Display name for UI in Spanish. */
    val displayName: String
        get() = when (this) {
            DIRECTORY -> "Carpetas"
            IMAGE -> "Imágenes"
            VIDEO -> "Videos"
            AUDIO -> "Audio"
            DOCUMENT -> "Documentos"
            ARCHIVE -> "Archivos comprimidos"
            APK -> "APKs"
            DOWNLOADS -> "Descargas"
            OTHER -> "Otros"
        }
}