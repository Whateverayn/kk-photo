package com.kk.kkphoto

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Size as AndroidSize
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.size.pxOrElse

private const val FALLBACK_THUMBNAIL_PX = 256

/**
 * MediaStoreのcontent:// Uriに対して、Coil標準のContentUriFetcher(元画像を毎回自前でデコード)ではなく
 * [ContentResolver.loadThumbnail]を使う。OSが保持するサムネイルキャッシュ(アプリ横断・再起動後も残る)を
 * 活かしつつ、デコード結果自体はCoilのメモリキャッシュに乗せることで両方の利点を得る。
 */
class MediaStoreThumbnailFetcher(
    private val context: Context,
    private val uri: Uri,
    private val options: Options
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val width = options.size.width.pxOrElse { FALLBACK_THUMBNAIL_PX }
        val height = options.size.height.pxOrElse { FALLBACK_THUMBNAIL_PX }
        val bitmap = context.contentResolver.loadThumbnail(uri, AndroidSize(width, height), null)
        return ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = true,
            dataSource = DataSource.DISK
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != ContentResolver.SCHEME_CONTENT) return null
            if (data.authority != MediaStore.AUTHORITY) return null
            return MediaStoreThumbnailFetcher(context, data, options)
        }
    }
}
