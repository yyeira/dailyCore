package com.yyeira.dailycollage.model

import android.net.Uri
import java.time.Instant
import java.time.ZoneId

data class GalleryImage(
    val uri: Uri,
    val takenAtMillis: Long,
) {
    val dateKey: String
        get() = Instant.ofEpochMilli(takenAtMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toString()
}
