package com.yyeira.dailycollage.domain

import android.content.ContentResolver
import android.graphics.BitmapFactory
import com.yyeira.dailycollage.model.GalleryImage
import com.yyeira.dailycollage.model.ImageDimensions

class ImageDimensionResolver(
    private val contentResolver: ContentResolver,
) {
    fun resolve(images: List<GalleryImage>): List<Pair<GalleryImage, ImageDimensions>> {
        return images.map { image ->
            image to resolveOne(image)
        }
    }

    private fun resolveOne(image: GalleryImage): ImageDimensions {
        contentResolver.openInputStream(image.uri)?.use { inputStream ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                return ImageDimensions(options.outWidth, options.outHeight)
            }
        }
        return ImageDimensions(1, 1)
    }
}
