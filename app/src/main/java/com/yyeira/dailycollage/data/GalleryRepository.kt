package com.yyeira.dailycollage.data

import android.content.ContentResolver
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import com.yyeira.dailycollage.model.GalleryImage
import java.time.LocalDate
import java.time.ZoneId

class GalleryRepository(
    private val contentResolver: ContentResolver,
) {
    fun queryImages(startDate: LocalDate, endDate: LocalDate): List<GalleryImage> {
        val zone = ZoneId.systemDefault()
        val startMillis = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
        )

        val selection = buildString {
            append("(")
            append("${MediaStore.Images.Media.DATE_TAKEN} >= ? AND ${MediaStore.Images.Media.DATE_TAKEN} <= ?")
            append(" OR (")
            append("${MediaStore.Images.Media.DATE_TAKEN} = 0 AND ")
            append("${MediaStore.Images.Media.DATE_ADDED} * 1000 >= ? AND ")
            append("${MediaStore.Images.Media.DATE_ADDED} * 1000 <= ?")
            append(")")
            append(")")
        }

        val selectionArgs = arrayOf(
            startMillis.toString(),
            endMillis.toString(),
            startMillis.toString(),
            endMillis.toString(),
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} ASC, ${MediaStore.Images.Media.DATE_ADDED} ASC"

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val images = mutableListOf<GalleryImage>()

        contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val dateTaken = cursor.getLong(dateTakenColumn)
                val dateAdded = cursor.getLong(dateAddedColumn)
                val takenAtMillis = if (dateTaken > 0) dateTaken else dateAdded * 1000

                if (takenAtMillis in startMillis..endMillis) {
                    val uri = ContentUris.withAppendedId(collection, id)
                    images.add(GalleryImage(uri = uri, takenAtMillis = takenAtMillis))
                }
            }
        }

        return images
    }
}
