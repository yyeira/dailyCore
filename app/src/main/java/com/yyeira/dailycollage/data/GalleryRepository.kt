package com.yyeira.dailycollage.data

import android.content.ContentResolver
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import com.yyeira.dailycollage.model.Album
import com.yyeira.dailycollage.model.GalleryImage
import java.time.LocalDate
import java.time.ZoneId

class GalleryRepository(
    private val contentResolver: ContentResolver,
) {
    private val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    fun queryAlbums(startDate: LocalDate, endDate: LocalDate): List<Album> {
        val (startMillis, endMillis) = dateRangeMillis(startDate, endDate)
        val projection = arrayOf(
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        )
        val (selection, selectionArgs) = dateSelectionClause(startMillis, endMillis)
        val counts = mutableMapOf<Long, Pair<String, Int>>()

        contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(bucketIdCol)
                val name = cursor.getString(bucketNameCol) ?: "Unknown"
                val (existingName, count) = counts[id] ?: (name to 0)
                counts[id] = existingName to count + 1
            }
        }

        return counts.map { (id, pair) -> Album(id, pair.first, pair.second) }
            .sortedByDescending { it.imageCount }
    }

    fun queryImages(
        startDate: LocalDate,
        endDate: LocalDate,
        bucketIds: Set<Long>? = null,
    ): List<GalleryImage> {
        val (startMillis, endMillis) = dateRangeMillis(startDate, endDate)
        val (dateSelection, dateArgs) = dateSelectionClause(startMillis, endMillis)

        val selection: String
        val selectionArgs: Array<String>
        if (bucketIds != null && bucketIds.isNotEmpty()) {
            val placeholders = bucketIds.joinToString(",") { "?" }
            selection = "($dateSelection) AND ${MediaStore.Images.Media.BUCKET_ID} IN ($placeholders)"
            selectionArgs = dateArgs + bucketIds.map { it.toString() }.toTypedArray()
        } else {
            selection = dateSelection
            selectionArgs = dateArgs
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} ASC, ${MediaStore.Images.Media.DATE_ADDED} ASC"
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

    private fun dateRangeMillis(startDate: LocalDate, endDate: LocalDate): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val startMillis = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return startMillis to endMillis
    }

    private fun dateSelectionClause(startMillis: Long, endMillis: Long): Pair<String, Array<String>> {
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
        val args = arrayOf(
            startMillis.toString(),
            endMillis.toString(),
            startMillis.toString(),
            endMillis.toString(),
        )
        return selection to args
    }
}
