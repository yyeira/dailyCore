package com.yyeira.dailycollage.domain

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import com.yyeira.dailycollage.model.CollageLayout
import com.yyeira.dailycollage.model.CropOffset
import com.yyeira.dailycollage.model.GalleryImage
import com.yyeira.dailycollage.model.OutputAspectRatio
import com.yyeira.dailycollage.util.CollageLogger
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
        showCellTimeLabels: Boolean = false,
    ): Bitmap {
        require(images.isNotEmpty()) { "images must not be empty" }

        val isGrid9 = layout.description == LAYOUT_GRID_9
        val backgroundColor = Color.BLACK
        val gridGap = if (isGrid9) CollageLayoutPlanner.GRID_9_GAP else DEFAULT_GRID_GAP
        val emptySlotCount = if (isGrid9) 0 else {
            val totalGridSlots = layout.cells.size
            (totalGridSlots - images.size).coerceAtLeast(0)
        }

        CollageLogger.d(
            "createCollage start dateKey=$dateKey layout=${layout.description} " +
                "images=${images.size} cells=${layout.cells.size} " +
                "showCellTimeLabels=$showCellTimeLabels ratio=$outputAspectRatio",
        )

        val scale = canvasWidth.toFloat() / layout.canvasWidth.toFloat()
        val outputHeight = (layout.canvasHeight * scale).toInt().coerceAtLeast(1)
        val natural = Bitmap.createBitmap(canvasWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(natural)
        canvas.drawColor(backgroundColor)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        var decodedCount = 0
        var failedDecodeCount = 0

        layout.cells.forEach { cell ->
            val image = images.getOrNull(cell.imageIndex) ?: return@forEach
            val slotLeft = (cell.left * scale).toInt()
            val slotTop = (cell.top * scale).toInt()
            val slotWidth = (cell.width * scale).toInt().coerceAtLeast(1)
            val slotHeight = (cell.height * scale).toInt().coerceAtLeast(1)

            val bitmap = decodeScaledBitmap(image, slotWidth, slotHeight)
            if (bitmap != null) {
                decodedCount++
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
            } else {
                failedDecodeCount++
                CollageLogger.w(
                    "decode failed dateKey=$dateKey imageIndex=${cell.imageIndex} uri=${image.uri}",
                )
            }
        }

        val naturalWidth = canvasWidth
        val naturalHeight = outputHeight
        val fitted = OutputAspectRatioFitter.apply(
            source = natural,
            aspectRatio = outputAspectRatio,
            canvasWidth = canvasWidth,
            backgroundColor = backgroundColor,
        )
        val aspectFitterApplied = fitted !== natural
        if (aspectFitterApplied && !natural.isRecycled) {
            natural.recycle()
        }

        val finalCanvas = Canvas(fitted)
        var labelDrawCount = 0
        if (showCellTimeLabels) {
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
                if (mapped.width() > 10 && mapped.height() > 10) {
                    drawCellTimeLabel(
                        canvas = finalCanvas,
                        image = image,
                        slotLeft = mapped.left,
                        slotTop = mapped.top,
                        slotWidth = mapped.width(),
                        slotHeight = mapped.height(),
                        showDate = showDate,
                    )
                    labelDrawCount++
                } else {
                    CollageLogger.w(
                        "skip cell label dateKey=$dateKey imageIndex=${cell.imageIndex} " +
                            "mapped=${mapped.width()}x${mapped.height()}",
                    )
                }
            }
        }
        val distinctDates = images.map { it.dateKey }.distinct()
        val drawWatermark = !(showCellTimeLabels && distinctDates.size == 1)
        if (drawWatermark) {
            drawDateWatermark(finalCanvas, dateKey, fitted.width, fitted.height)
        }

        CollageLogger.reportRenderDiagnostics(
            dateKey = dateKey,
            layoutDescription = layout.description,
            imageCount = images.size,
            cellCount = layout.cells.size,
            decodedCount = decodedCount,
            failedDecodeCount = failedDecodeCount,
            emptySlotCount = emptySlotCount,
            naturalSize = "${naturalWidth}x$naturalHeight",
            finalSize = "${fitted.width}x${fitted.height}",
            outputAspectRatio = outputAspectRatio.name,
            aspectFitterApplied = aspectFitterApplied,
            backgroundColor = if (backgroundColor == Color.WHITE) "WHITE" else "BLACK",
            gapPx = gridGap,
            showCellTimeLabels = showCellTimeLabels,
            labelDrawCount = labelDrawCount,
            dateWatermarkDrawn = drawWatermark,
        )

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

    private fun drawOutlinedText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        textSize: Float,
    ) {
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            this.textSize = textSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            style = Paint.Style.STROKE
            strokeWidth = (textSize * 0.1f).coerceIn(1.5f, 4f)
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            style = Paint.Style.FILL
        }
        canvas.drawText(text, x, y, strokePaint)
        canvas.drawText(text, x, y, fillPaint)
    }

    /**
     * 半透明黑底 + 纯白字，不用描边。
     * 描边 + 小字号时 "HH:mm" 的冒号会被渲染成白点/白圈。
     */
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
        val padding = slotWidth * 0.05f
        val textSize = (slotWidth * 0.07f).coerceIn(11f, 22f)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val fontMetrics = textPaint.fontMetrics
        val textWidth = textPaint.measureText(label)
        val textHeight = fontMetrics.descent - fontMetrics.ascent

        val pillPadH = textSize * 0.28f
        val pillPadV = textSize * 0.18f
        val pillLeft = slotLeft + padding
        val pillBottom = slotTop + slotHeight - padding
        val pillTop = pillBottom - textHeight - pillPadV * 2f
        val pillRight = pillLeft + textWidth + pillPadH * 2f

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(150, 0, 0, 0)
        }
        canvas.drawRoundRect(
            RectF(pillLeft, pillTop, pillRight, pillBottom),
            textSize * 0.22f,
            textSize * 0.22f,
            bgPaint,
        )

        val textX = pillLeft + pillPadH
        val textY = pillBottom - pillPadV - fontMetrics.descent
        canvas.drawText(label, textX, textY, textPaint)
    }

    private fun drawDateWatermark(canvas: Canvas, dateKey: String, canvasWidth: Int, canvasHeight: Int) {
        val padding = canvasWidth * 0.02f
        val textSize = canvasWidth * 0.04f

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = textSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val textWidth = textPaint.measureText(dateKey)
        val textX = canvasWidth - textWidth - padding
        val textY = canvasHeight - padding

        drawOutlinedText(canvas, dateKey, textX, textY, textSize)
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
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
                    }
                }
                return BitmapFactory.decodeStream(decodeStream, null, decodeOptions)?.toSdrBitmap()
            }
        }

        return null
    }

    /** 将 Ultra HDR / 非常规位图压成普通 ARGB，避免绘制出异常高光白点 */
    private fun Bitmap.toSdrBitmap(): Bitmap {
        val hasGainmap = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && hasGainmap()
        val needsFlatten = hasGainmap || config != Bitmap.Config.ARGB_8888
        if (!needsFlatten) return this

        val flat = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(flat).drawBitmap(this, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        if (!isRecycled) recycle()
        return flat
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
        private const val LAYOUT_GRID_9 = "layout_grid_9"
        private const val DEFAULT_GRID_GAP = 6
    }
}
