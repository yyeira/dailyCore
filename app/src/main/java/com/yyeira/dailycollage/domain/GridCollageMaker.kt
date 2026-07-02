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
import com.yyeira.dailycollage.model.CollageLayout
import com.yyeira.dailycollage.model.GalleryImage
import com.yyeira.dailycollage.model.OutputAspectRatio
import kotlin.math.max
import kotlin.math.min

class GridCollageMaker(
    private val contentResolver: ContentResolver,
    private val canvasWidth: Int = 1080,
) {
    fun createCollage(
        images: List<GalleryImage>,
        layout: CollageLayout,
        dateKey: String,
        outputAspectRatio: OutputAspectRatio = OutputAspectRatio.NATURAL,
    ): Bitmap {
        require(images.isNotEmpty()) { "images must not be empty" }

        val scale = canvasWidth.toFloat() / layout.canvasWidth.toFloat()
        val outputHeight = (layout.canvasHeight * scale).toInt().coerceAtLeast(1)
        val natural = Bitmap.createBitmap(canvasWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(natural)
        canvas.drawColor(BACKGROUND_COLOR)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        layout.cells.forEach { cell ->
            val image = images.getOrNull(cell.imageIndex) ?: return@forEach
            val slotLeft = (cell.left * scale).toInt()
            val slotTop = (cell.top * scale).toInt()
            val slotWidth = (cell.width * scale).toInt().coerceAtLeast(1)
            val slotHeight = (cell.height * scale).toInt().coerceAtLeast(1)

            val bitmap = decodeScaledBitmap(image, slotWidth, slotHeight)
            if (bitmap != null) {
                try {
                    val dstRect = fitCenterDstRect(
                        bitmap.width,
                        bitmap.height,
                        slotLeft,
                        slotTop,
                        slotWidth,
                        slotHeight,
                    )
                    canvas.drawBitmap(bitmap, null, dstRect, paint)
                } finally {
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
            }
        }

        val fitted = OutputAspectRatioFitter.apply(natural, outputAspectRatio, canvasWidth)
        if (fitted !== natural && !natural.isRecycled) {
            natural.recycle()
        }

        val finalCanvas = Canvas(fitted)
        drawDateWatermark(finalCanvas, dateKey, fitted.width, fitted.height)
        return fitted
    }

    private fun fitCenterDstRect(
        bitmapWidth: Int,
        bitmapHeight: Int,
        slotLeft: Int,
        slotTop: Int,
        slotWidth: Int,
        slotHeight: Int,
    ): Rect {
        val scale = min(
            slotWidth.toFloat() / bitmapWidth,
            slotHeight.toFloat() / bitmapHeight,
        )
        val drawWidth = (bitmapWidth * scale).toInt().coerceAtLeast(1)
        val drawHeight = (bitmapHeight * scale).toInt().coerceAtLeast(1)
        val drawLeft = slotLeft + (slotWidth - drawWidth) / 2
        val drawTop = slotTop + (slotHeight - drawHeight) / 2
        return Rect(drawLeft, drawTop, drawLeft + drawWidth, drawTop + drawHeight)
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

    companion object {
        private val BACKGROUND_COLOR = Color.parseColor("#121212")
    }
}
