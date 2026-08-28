package com.kk.kkphoto

import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PhotoEntry(val id: Long, val displayName: String)

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
        arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME),
        DATE_RANGE_SELECTION,
        selectionArgs,
        null
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        while (cursor.moveToNext()) {
            result.add(PhotoEntry(cursor.getLong(idCol), cursor.getString(nameCol)))
        }
    }
    result
}
