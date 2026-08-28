package com.kk.kkphoto

import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PhotoEntry(
    val id: Long,
    val displayName: String,
    val size: Long,
    val dateModified: Long
)

private const val DATE_RANGE_SELECTION =
    "(CASE WHEN ${MediaStore.Images.Media.DATE_TAKEN} IS NOT NULL AND " +
        "${MediaStore.Images.Media.DATE_TAKEN} > 0 THEN ${MediaStore.Images.Media.DATE_TAKEN} " +
        "ELSE ${MediaStore.Images.Media.DATE_ADDED} * 1000 END) " +
        "BETWEEN CAST(? AS INTEGER) AND CAST(? AS INTEGER)"

suspend fun queryPhotosInRange(
    context: Context,
    startMillis: Long,
    endMillis: Long
): List<PhotoEntry> = withContext(Dispatchers.IO) {
    val result = mutableListOf<PhotoEntry>()
    val selectionArgs = arrayOf(startMillis.toString(), endMillis.toString())
    context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_MODIFIED
        ),
        DATE_RANGE_SELECTION,
        selectionArgs,
        null
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
        val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
        while (cursor.moveToNext()) {
            result.add(
                PhotoEntry(
                    id = cursor.getLong(idCol),
                    displayName = cursor.getString(nameCol),
                    size = cursor.getLong(sizeCol),
                    dateModified = cursor.getLong(dateModifiedCol)
                )
            )
        }
    }
    result
}
