package com.yyeira.dailycollage.util

import android.content.Context
import com.yyeira.dailycollage.R

object LayoutDescriptionFormatter {
    fun format(context: Context, descriptionKey: String): String {
        val resId = when (descriptionKey) {
            "layout_single" -> R.string.layout_single
            "layout_horizontal" -> R.string.layout_horizontal
            "layout_vertical" -> R.string.layout_vertical
            "layout_hero_top" -> R.string.layout_hero_top
            "layout_grid_2x2" -> R.string.layout_grid_2x2
            "layout_left_big" -> R.string.layout_left_big
            "layout_auto_row_2" -> R.string.layout_auto_row_2
            "layout_auto_row_3" -> R.string.layout_auto_row_3
            "layout_auto_row_4" -> R.string.layout_auto_row_4
            else -> 0
        }
        return if (resId != 0) context.getString(resId) else descriptionKey
    }
}
