package com.yyeira.dailycollage.model

import android.graphics.Bitmap

data class DayPreview(
    val dateKey: String,
    val imageCount: Int,
    val layoutDescription: String,
    val previewBitmap: Bitmap,
    val images: List<GalleryImage>,
    val layout: CollageLayout,
)
