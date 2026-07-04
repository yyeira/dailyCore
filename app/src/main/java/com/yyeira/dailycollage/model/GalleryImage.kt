package com.yyeira.dailycollage.model

import android.net.Uri
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class GalleryImage(
    val uri: Uri,
    val takenAtMillis: Long,
) {
    val dateKey: String
        get() = takenAtMillis.toLocalDateTime().toLocalDate().toString()

    fun formatTime(zoneId: ZoneId = ZoneId.systemDefault()): String {
        return takenAtMillis.toLocalDateTime(zoneId).format(TIME_FORMATTER)
    }

    fun formatCellTimeLabel(showDate: Boolean, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val dateTime = takenAtMillis.toLocalDateTime(zoneId)
        return if (showDate) {
            dateTime.format(DATE_TIME_FORMATTER)
        } else {
            dateTime.format(TIME_FORMATTER)
        }
    }

    private fun Long.toLocalDateTime(zoneId: ZoneId = ZoneId.systemDefault()) =
        Instant.ofEpochMilli(this).atZone(zoneId).toLocalDateTime()

    companion object {
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
        private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("M/d HH:mm")
    }
}
