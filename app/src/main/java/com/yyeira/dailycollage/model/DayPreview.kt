package com.yyeira.dailycollage.model

import android.graphics.Bitmap

data class DayPreview(
    val dateKey: String,
    val imageCount: Int,
    val layoutDescription: String,
    val previewBitmap: Bitmap,
    val images: List<GalleryImage>,
    val layout: CollageLayout,
    val cropOffsets: Map<Int, CropOffset> = emptyMap(),
    val originalImages: List<GalleryImage> = images,
    val showCellTimeLabels: Boolean = false,
) {
    val isModified: Boolean
        get() = images != originalImages || cropOffsets.isNotEmpty()
}
