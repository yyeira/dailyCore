package com.yyeira.dailycollage.util

import android.util.Log

object CollageLogger {
    const val TAG = "DailyCollage"

    fun d(message: String) {
        Log.d(TAG, message)
    }

    fun i(message: String) {
        Log.i(TAG, message)
    }

    fun w(message: String) {
        Log.w(TAG, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, message, throwable)
        } else {
            Log.e(TAG, message)
        }
    }

    /**
     * 汇总拼图渲染诊断，便于 logcat 过滤 TAG=DailyCollage 排查黑白异常。
     */
    fun reportRenderDiagnostics(
        dateKey: String,
        layoutDescription: String,
        imageCount: Int,
        cellCount: Int,
        decodedCount: Int,
        failedDecodeCount: Int,
        emptySlotCount: Int,
        naturalSize: String,
        finalSize: String,
        outputAspectRatio: String,
        aspectFitterApplied: Boolean,
        backgroundColor: String,
        gapPx: Int,
        showCellTimeLabels: Boolean,
        labelDrawCount: Int,
        dateWatermarkDrawn: Boolean,
    ) {
        val issues = buildList {
            if (failedDecodeCount > 0) {
                add("decode_failed=$failedDecodeCount → 对应格子会露出背景色")
            }
            if (emptySlotCount > 0) {
                add("empty_grid_slots=$emptySlotCount → 末行未满格会露出背景色")
            }
            if (gapPx > 0) {
                add("grid_gap=${gapPx}px → 格间会显示背景色")
            }
            if (aspectFitterApplied) {
                add("aspect_fitter=on → 非自然比例时四周可能铺背景色")
            }
            if (showCellTimeLabels && labelDrawCount > 0) {
                add("cell_time_labels=$labelDrawCount → 每格左下角时间标签(黑底白字)")
            }
            if (dateWatermarkDrawn) {
                add("date_watermark=on → 右下角白字+黑描边")
            }
        }

        i(
            buildString {
                append("render dateKey=$dateKey layout=$layoutDescription ")
                append("images=$imageCount cells=$cellCount decoded=$decodedCount ")
                append("natural=$naturalSize final=$finalSize ratio=$outputAspectRatio ")
                append("bg=$backgroundColor gap=$gapPx labels=$showCellTimeLabels($labelDrawCount) ")
                append("watermark=$dateWatermarkDrawn fitter=$aspectFitterApplied")
                if (issues.isNotEmpty()) {
                    append(" | 黑白来源: ")
                    append(issues.joinToString("; "))
                }
            },
        )
    }
}
