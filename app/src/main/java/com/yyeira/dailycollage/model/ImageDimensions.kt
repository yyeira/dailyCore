package com.yyeira.dailycollage.model

data class ImageDimensions(
    val width: Int,
    val height: Int,
) {
    val aspectRatio: Float
        get() = if (height > 0) width.toFloat() / height else 1f

    val isPortrait: Boolean
        get() = aspectRatio < 0.9f

    val isLandscape: Boolean
        get() = aspectRatio > 1.1f
}
