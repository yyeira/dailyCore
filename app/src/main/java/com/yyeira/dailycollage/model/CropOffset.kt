package com.yyeira.dailycollage.model

/**
 * Normalized crop anchor and zoom.
 * x/y: 0.0 = top/left edge, 1.0 = bottom/right edge, 0.5 = center.
 * scale: 1.0 = fill cell exactly (center-crop), >1.0 = zoom in further.
 */
data class CropOffset(
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val scale: Float = 1f,
) {
    fun coerced() = CropOffset(
        x.coerceIn(0f, 1f),
        y.coerceIn(0f, 1f),
        scale.coerceAtLeast(1f),
    )

    companion object {
        val CENTER = CropOffset(0.5f, 0.5f, 1f)
    }
}
