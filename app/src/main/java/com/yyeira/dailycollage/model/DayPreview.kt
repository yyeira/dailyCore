package com.yyeira.dailycollage.model

import android.graphics.Bitmap

data class DayPreview(
    val dateKey: String,
    val imageCount: Int,
    val previewBitmap: Bitmap,
    val images: List<GalleryImage>,
)
