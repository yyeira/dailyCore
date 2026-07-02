package com.yyeira.dailycollage.domain

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.yyeira.dailycollage.model.GalleryImage
import kotlin.math.ceil
import kotlin.math.max

class GridCollageMaker(
    private val contentResolver: ContentResolver,
    private val canvasWidth: Int = 1080,
) {
    fun createCollage(images: List<GalleryImage>, columns: Int, dateKey: String): Bitmap {
        require(columns > 0) { "columns must be positive" }
        require(images.isNotEmpty()) { "images must not be empty" }

        val cellWidth = canvasWidth / columns
        val cellHeight = cellWidth
        val rows = ceil(images.size.toDouble() / columns).toInt()
        val canvasHeight = rows * cellHeight

        val output = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        images.forEachIndexed { index, image ->
            val row = index / columns
            val col = index % columns
            val left = col * cellWidth
            val top = row * cellHeight

            val bitmap = decodeScaledBitmap(image, cellWidth, cellHeight)
            if (bitmap != null) {
                try {
                    val (srcRect, dstRect) = centerCropRects(
                        bitmap.width,
                        bitmap.height,
                        left,
                        top,
                        cellWidth,
                        cellHeight,
                    )
                    canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
                } finally {
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
            }
        }

        drawDateWatermark(canvas, dateKey, canvasWidth, canvasHeight)
        return output
    }

    private fun drawDateWatermark(canvas: Canvas, dateKey: String, canvasWidth: Int, canvasHeight: Int) {
        val padding = canvasWidth * 0.02f
        val textSize = canvasWidth * 0.045f

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(6f, 0f, 2f, Color.argb(180, 0, 0, 0))
        }

        val textWidth = textPaint.measureText(dateKey)
        val textX = canvasWidth - textWidth - padding
        val textY = canvasHeight - padding

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(140, 0, 0, 0)
        }
        val bgRect = RectF(
            textX - padding * 0.6f,
            textY - textSize - padding * 0.4f,
            canvasWidth - padding * 0.4f,
            canvasHeight - padding * 0.3f,
        )
        canvas.drawRoundRect(bgRect, padding * 0.4f, padding * 0.4f, bgPaint)
        canvas.drawText(dateKey, textX, textY, textPaint)
    }

    private fun decodeScaledBitmap(image: GalleryImage, targetWidth: Int, targetHeight: Int): Bitmap? {
        val maxDimension = max(targetWidth, targetHeight) * 2

        contentResolver.openInputStream(image.uri)?.use { inputStream ->
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, boundsOptions)

            val sampleSize = calculateInSampleSize(
                boundsOptions.outWidth,
                boundsOptions.outHeight,
                maxDimension,
                maxDimension,
            )

            contentResolver.openInputStream(image.uri)?.use { decodeStream ->
                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                return BitmapFactory.decodeStream(decodeStream, null, decodeOptions)
            }
        }

        return null
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int,
    ): Int {
        var inSampleSize = 1
        if (width > reqWidth || height > reqHeight) {
            var halfWidth = width / 2
            var halfHeight = height / 2
            while (halfWidth / inSampleSize >= reqWidth && halfHeight / inSampleSize >= reqHeight) {
                inSampleSize *= 2
            }
        }
        return max(inSampleSize, 1)
    }

    private fun centerCropRects(
        bitmapWidth: Int,
        bitmapHeight: Int,
        left: Int,
        top: Int,
        cellWidth: Int,
        cellHeight: Int,
    ): Pair<Rect, Rect> {
        val dstRect = Rect(left, top, left + cellWidth, top + cellHeight)
        val bitmapRatio = bitmapWidth.toFloat() / bitmapHeight
        val cellRatio = cellWidth.toFloat() / cellHeight

        val srcRect = if (bitmapRatio > cellRatio) {
            val cropWidth = (bitmapHeight * cellRatio).toInt()
            val cropLeft = (bitmapWidth - cropWidth) / 2
            Rect(cropLeft, 0, cropLeft + cropWidth, bitmapHeight)
        } else {
            val cropHeight = (bitmapWidth / cellRatio).toInt()
            val cropTop = (bitmapHeight - cropHeight) / 2
            Rect(0, cropTop, bitmapWidth, cropTop + cropHeight)
        }

        return srcRect to dstRect
    }
}
