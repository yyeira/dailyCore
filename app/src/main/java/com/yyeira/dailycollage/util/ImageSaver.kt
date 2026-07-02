package com.yyeira.dailycollage.util

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import java.io.IOException

class ImageSaver(
    private val contentResolver: ContentResolver,
) {
    fun saveJpeg(bitmap: Bitmap, fileName: String): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "$PICTURES_PATH/")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val uri = contentResolver.insert(collection, values) ?: return false

        return try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            } ?: return false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val pendingClear = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                contentResolver.update(uri, pendingClear, null, null)
            }
            true
        } catch (_: IOException) {
            contentResolver.delete(uri, null, null)
            false
        }
    }

    companion object {
        private const val PICTURES_PATH = "Pictures/DailyCollage"
        private const val JPEG_QUALITY = 90
    }
}
