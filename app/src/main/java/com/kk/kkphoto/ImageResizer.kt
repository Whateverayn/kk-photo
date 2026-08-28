package com.kk.kkphoto

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class ResizePreset(val label: String, val megapixels: Double) {
    SMALL("小 (0.3Mpx)", 0.3),
    MEDIUM("中 (1Mpx)", 1.0),
    LARGE("大 (2Mpx)", 2.0)
}

private const val JPEG_QUALITY = 92

private fun computeInSampleSize(rawWidth: Int, rawHeight: Int, reqWidth: Int, reqHeight: Int): Int {
    var inSampleSize = 1
    if (rawHeight > reqHeight || rawWidth > reqWidth) {
        var halfHeight = rawHeight / 2
        var halfWidth = rawWidth / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

private fun outputFileName(id: Long, displayName: String): String {
    val base = displayName.substringBeforeLast('.', displayName)
    return "${id}_$base.jpg"
}

/**
 * 元画像をデコードし、[targetMegapixels]の画素数に収まるようアスペクト比を保ったまま縮小してJPEGとして保存する。
 * 元画像が既に目標より小さい場合は拡大しない。
 */
suspend fun resizeAndSave(
    context: Context,
    photo: PhotoEntry,
    targetMegapixels: Double
): Unit = withContext(Dispatchers.IO) {
    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, photo.id)
    val resolver = context.contentResolver

    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOptions) }
    val rawWidth = boundsOptions.outWidth
    val rawHeight = boundsOptions.outHeight
    check(rawWidth > 0 && rawHeight > 0) { "画像サイズを取得できません: ${photo.displayName}" }

    val targetPixels = targetMegapixels * 1_000_000
    val sourcePixels = rawWidth.toLong() * rawHeight.toLong()
    val scale = min(1.0, sqrt(targetPixels / sourcePixels))
    val finalWidth = (rawWidth * scale).roundToInt().coerceAtLeast(1)
    val finalHeight = (rawHeight * scale).roundToInt().coerceAtLeast(1)

    val sampleSize = computeInSampleSize(rawWidth, rawHeight, finalWidth, finalHeight)
    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val coarseBitmap = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, decodeOptions)
    } ?: error("デコードに失敗しました: ${photo.displayName}")

    val finalBitmap = if (coarseBitmap.width == finalWidth && coarseBitmap.height == finalHeight) {
        coarseBitmap
    } else {
        Bitmap.createScaledBitmap(coarseBitmap, finalWidth, finalHeight, true).also {
            if (it !== coarseBitmap) coarseBitmap.recycle()
        }
    }

    val outputDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "resized")
    if (!outputDir.exists()) outputDir.mkdirs()
    val outputFile = File(outputDir, outputFileName(photo.id, photo.displayName))
    FileOutputStream(outputFile).use { out ->
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
    }
    finalBitmap.recycle()
}
