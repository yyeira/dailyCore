package com.yyeira.dailycollage.domain

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.yyeira.dailycollage.model.OutputAspectRatio
import kotlin.math.max

object OutputAspectRatioFitter {

    fun apply(source: Bitmap, aspectRatio: OutputAspectRatio, canvasWidth: Int): Bitmap {
        if (aspectRatio.isNatural) {
            return source
        }

        val targetHeight = (canvasWidth.toFloat() * aspectRatio.heightRatio / aspectRatio.widthRatio)
            .toInt()
            .coerceAtLeast(1)

        val output = Bitmap.createBitmap(canvasWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val scale = max(
            canvasWidth.toFloat() / source.width,
            targetHeight.toFloat() / source.height,
        )
        val drawWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val drawHeight = (source.height * scale).toInt().coerceAtLeast(1)
        val left = (canvasWidth - drawWidth) / 2
        val top = (targetHeight - drawHeight) / 2

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, null, Rect(left, top, left + drawWidth, top + drawHeight), paint)
        return output
    }
}
