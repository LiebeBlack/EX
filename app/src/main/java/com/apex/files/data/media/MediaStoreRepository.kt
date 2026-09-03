package com.apex.files.data.media

import android.content.Context
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import com.apex.files.data.model.Category
import com.apex.files.data.model.FileNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MediaStore-backed access for the physical categories (Imágenes, Vídeos,
 * Audio). Nodes carry real content URIs so Coil and share intents work
 * directly. Returns empty lists when the corresponding permission is missing.
 */
class MediaStoreRepository(private val context: Context) {

    private val resolver get() = context.contentResolver

    private fun collection(category: Category): Uri? = when (category) {
        Category.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        Category.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        Category.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        else -> null
    }

    suspend fun list(category: Category, limit: Int = 3000): List<FileNode> =
        withContext(Dispatchers.IO) {
            val uri = collection(category) ?: return@withContext emptyList()
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED,
            )
            val nodes = ArrayList<FileNode>(minOf(limit, 512))
            try {
                resolver.query(uri, projection, null, null, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")
                    ?.use { cursor ->
                        val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                        while (cursor.moveToNext() && nodes.size < limit) {
                            val id = cursor.getLong(idCol)
                            val name = cursor.getString(nameCol) ?: "item_$id"
                            val size = if (cursor.isNull(sizeCol)) 0L else cursor.getLong(sizeCol)
                            val date = if (cursor.isNull(dateCol)) 0L else cursor.getLong(dateCol) * 1000L
                            nodes.add(
                                FileNode(
                                    name = name,
                                    path = "Media/${category.name}/$name",
                                    isDir = false,
                                    size = size,
                                    lastModified = date,
                                    extension = name.substringAfterLast('.', "").lowercase(),
                                    category = category,
                                    uri = ContentUris.withAppendedId(uri, id),
                                )
                            )
                        }
                    }
            } catch (e: SecurityException) {
                return@withContext emptyList()
            }
            nodes
        }

    suspend fun count(category: Category): Int = withContext(Dispatchers.IO) {
        val uri = collection(category) ?: return@withContext 0
        try {
            resolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns._ID),
                null,
                null,
                null,
            )?.use { cursor -> cursor.count } ?: 0
        } catch (e: SecurityException) {
            0
        }
    }
}