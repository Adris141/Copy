package com.hola.backup.data

import android.content.Context
import android.net.Uri
import android.provider.MediaStore

class MediaReader(private val context: Context) {
    data class MediaFile(
        val uri: Uri,
        val displayName: String,
        val mimeType: String?,
        val sizeBytes: Long,
        val modifiedAtMillis: Long
    )

    fun readRecent(limit: Int): List<MediaFile> {
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?, ?)"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO.toString()
        )

        val out = mutableListOf<MediaFile>()
        context.contentResolver.query(
            uri,
            projection,
            selection,
            args,
            "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

            while (cursor.moveToNext() && out.size < limit) {
                val id = cursor.getLong(idIndex)
                out += MediaFile(
                    uri = Uri.withAppendedPath(uri, id.toString()),
                    displayName = cursor.getString(nameIndex) ?: "file_$id",
                    mimeType = cursor.getString(mimeIndex),
                    sizeBytes = cursor.getLong(sizeIndex),
                    modifiedAtMillis = cursor.getLong(modifiedIndex) * 1000L
                )
            }
        }
        return out
    }
}
