package com.yyeira.dailycollage.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import kotlin.math.max

object ThumbnailDecoder {
    fun decodeLarge(contentResolver: ContentResolver, uri: Uri, maxSize: Int = 1200): Bitmap? {
        return decode(contentResolver, uri, maxSize)
    }

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
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
                    }
                }
                return BitmapFactory.decodeStream(decodeStream, null, decodeOptions)?.toSdrBitmap()
            }
        }
        return null
    }

    private fun Bitmap.toSdrBitmap(): Bitmap {
        val hasGainmap = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && hasGainmap()
        if (!hasGainmap && config == Bitmap.Config.ARGB_8888) return this
        val flat = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(flat).drawBitmap(this, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        if (!isRecycled) recycle()
        return flat
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
