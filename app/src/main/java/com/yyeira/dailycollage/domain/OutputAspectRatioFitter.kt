package com.yyeira.dailycollage.domain

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.yyeira.dailycollage.model.OutputAspectRatio
import com.yyeira.dailycollage.util.CollageLogger
import kotlin.math.max

object OutputAspectRatioFitter {

    fun apply(
        source: Bitmap,
        aspectRatio: OutputAspectRatio,
        canvasWidth: Int,
        backgroundColor: Int = Color.BLACK,
    ): Bitmap {
        if (aspectRatio.isNatural) {
            CollageLogger.d(
                "aspectFitter skip natural source=${source.width}x${source.height}",
            )
            return source
        }

        val targetHeight = (canvasWidth.toFloat() * aspectRatio.heightRatio / aspectRatio.widthRatio)
            .toInt()
            .coerceAtLeast(1)

        val output = Bitmap.createBitmap(canvasWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(backgroundColor)

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

        CollageLogger.d(
            "aspectFitter applied ratio=${aspectRatio.name} " +
                "source=${source.width}x${source.height} " +
                "output=${output.width}x${output.height} " +
                "bg=${if (backgroundColor == Color.WHITE) "WHITE" else "BLACK"} " +
                "letterbox=(${left},${top})",
        )
        return output
    }
}
