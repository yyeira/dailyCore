package com.yyeira.dailycollage.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlin.math.max

object ThumbnailDecoder {
    fun decode(contentResolver: ContentResolver, uri: Uri, maxSize: Int = 160): Bitmap? {
        contentResolver.openInputStream(uri)?.use { inputStream ->
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, boundsOptions)

            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                return null
            }

            val sampleSize = calculateInSampleSize(
                boundsOptions.outWidth,
                boundsOptions.outHeight,
                maxSize,
                maxSize,
            )

            contentResolver.openInputStream(uri)?.use { decodeStream ->
                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                return BitmapFactory.decodeStream(decodeStream, null, decodeOptions)
            }
        }
        return null
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int,
    ): Int {
        var inSampleSize = 1
        if (width > reqWidth || height > reqHeight) {
            var halfWidth = width / 2
            var halfHeight = height / 2
            while (halfWidth / inSampleSize >= reqWidth && halfHeight / inSampleSize >= reqHeight) {
                inSampleSize *= 2
            }
        }
        return max(inSampleSize, 1)
    }
}
