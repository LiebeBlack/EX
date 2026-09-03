package com.apex.files.data.model

/** Physical categories assigned by the regex-based [CategoryEngine]. */
enum class Category {
    DIRECTORY, IMAGE, VIDEO, AUDIO, DOCUMENT, ARCHIVE, APK, OTHER;

    /** Categories that are shown as Home filters / MediaStore collections. */
    val isMediaCollection: Boolean
        get() = this == IMAGE || this == VIDEO || this == AUDIO
}