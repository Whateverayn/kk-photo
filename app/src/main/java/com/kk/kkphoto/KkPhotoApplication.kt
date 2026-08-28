package com.kk.kkphoto

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader

class KkPhotoApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(MediaStoreThumbnailFetcher.Factory(context))
            }
            .build()
    }
}
