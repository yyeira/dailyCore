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
import com.yyeira.dailycollage.model.CropOffset
import com.yyeira.dailycollage.model.GalleryImage
import com.yyeira.dailycollage.model.OutputAspectRatio
import kotlin.math.max

class GridCollageMaker(
    private val contentResolver: ContentResolver,
    private val canvasWidth: Int = 1080,
) {
    fun createCollage(
        images: List<GalleryImage>,
        layout: CollageLayout,
        dateKey: String,
        outputAspectRatio: OutputAspectRatio = OutputAspectRatio.NATURAL,
        cropOffsets: Map<Int, CropOffset> = emptyMap(),
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
                    val offset = cropOffsets[cell.imageIndex] ?: CropOffset.CENTER
                    val (srcRect, dstRect) = centerCropRects(
                        bitmap.width,
                        bitmap.height,
                        slotLeft,
                        slotTop,
                        slotWidth,
                        slotHeight,
                        offset,
                    )
                    canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
                } finally {
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
            }
        }

        val naturalWidth = canvasWidth
        val naturalHeight = outputHeight
        val fitted = OutputAspectRatioFitter.apply(natural, outputAspectRatio, canvasWidth)
        if (fitted !== natural && !natural.isRecycled) {
            natural.recycle()
        }

        val finalCanvas = Canvas(fitted)
        if (layout.description == LAYOUT_GRID_9) {
            val showDate = images.map { it.dateKey }.distinct().size > 1
            layout.cells.forEach { cell ->
                val image = images.getOrNull(cell.imageIndex) ?: return@forEach
                val slotLeft = (cell.left * scale).toInt()
                val slotTop = (cell.top * scale).toInt()
                val slotWidth = (cell.width * scale).toInt().coerceAtLeast(1)
                val slotHeight = (cell.height * scale).toInt().coerceAtLeast(1)
                val mapped = mapRectThroughFitter(
                    slotLeft,
                    slotTop,
                    slotWidth,
                    slotHeight,
                    naturalWidth,
                    naturalHeight,
                    fitted.width,
                    fitted.height,
                )
                drawCellTimeLabel(
                    canvas = finalCanvas,
                    image = image,
                    slotLeft = mapped.left,
                    slotTop = mapped.top,
                    slotWidth = mapped.width(),
                    slotHeight = mapped.height(),
                    showDate = showDate,
                )
            }
        }
        drawDateWatermark(finalCanvas, dateKey, fitted.width, fitted.height)
        return fitted
    }

    private fun centerCropRects(
        bitmapWidth: Int,
        bitmapHeight: Int,
        slotLeft: Int,
        slotTop: Int,
        slotWidth: Int,
        slotHeight: Int,
        offset: CropOffset = CropOffset.CENTER,
    ): Pair<Rect, Rect> {
        val dstRect = Rect(slotLeft, slotTop, slotLeft + slotWidth, slotTop + slotHeight)
        val bitmapRatio = bitmapWidth.toFloat() / bitmapHeight
        val slotRatio = slotWidth.toFloat() / slotHeight
        val zoom = offset.scale.coerceAtLeast(1f)

        val baseCropW: Int
        val baseCropH: Int
        if (bitmapRatio > slotRatio) {
            baseCropH = bitmapHeight
            baseCropW = (bitmapHeight * slotRatio).toInt()
        } else {
            baseCropW = bitmapWidth
            baseCropH = (bitmapWidth / slotRatio).toInt()
        }

        val cropW = (baseCropW / zoom).toInt().coerceIn(1, bitmapWidth)
        val cropH = (baseCropH / zoom).toInt().coerceIn(1, bitmapHeight)

        val maxLeft = bitmapWidth - cropW
        val maxTop = bitmapHeight - cropH
        val cropLeft = (maxLeft * offset.x).toInt().coerceIn(0, maxLeft)
        val cropTop = (maxTop * offset.y).toInt().coerceIn(0, maxTop)

        val srcRect = Rect(cropLeft, cropTop, cropLeft + cropW, cropTop + cropH)
        return srcRect to dstRect
    }

    private fun mapRectThroughFitter(
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): Rect {
        val fitScale = max(
            targetWidth.toFloat() / sourceWidth,
            targetHeight.toFloat() / sourceHeight,
        )
        val drawWidth = sourceWidth * fitScale
        val drawHeight = sourceHeight * fitScale
        val offsetX = (targetWidth - drawWidth) / 2f
        val offsetY = (targetHeight - drawHeight) / 2f
        return Rect(
            (left * fitScale + offsetX).toInt(),
            (top * fitScale + offsetY).toInt(),
            ((left + width) * fitScale + offsetX).toInt(),
            ((top + height) * fitScale + offsetY).toInt(),
        )
    }

    private fun drawCellTimeLabel(
        canvas: Canvas,
        image: GalleryImage,
        slotLeft: Int,
        slotTop: Int,
        slotWidth: Int,
        slotHeight: Int,
        showDate: Boolean,
    ) {
        val label = image.formatCellTimeLabel(showDate)
        val padding = slotWidth * 0.06f
        val textSize = slotWidth * 0.11f.coerceIn(18f, 36f)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(4f, 0f, 1f, Color.argb(180, 0, 0, 0))
        }

        val textWidth = textPaint.measureText(label)
        val textX = slotLeft + padding
        val textY = slotTop + slotHeight - padding

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(140, 0, 0, 0)
        }
        val bgRect = RectF(
            textX - padding * 0.4f,
            textY - textSize - padding * 0.3f,
            textX + textWidth + padding * 0.4f,
            slotTop + slotHeight - padding * 0.2f,
        )
        canvas.drawRoundRect(bgRect, padding * 0.3f, padding * 0.3f, bgPaint)
        canvas.drawText(label, textX, textY, textPaint)
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
        private const val LAYOUT_GRID_9 = "layout_grid_9"
    }
}
