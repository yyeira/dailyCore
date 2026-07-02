package com.yyeira.dailycollage.model

enum class OutputAspectRatio(
    val widthRatio: Int,
    val heightRatio: Int,
) {
    NATURAL(0, 0),
    RATIO_1_1(1, 1),
    RATIO_4_3(4, 3),
    RATIO_3_4(3, 4),
    RATIO_16_9(16, 9),
    RATIO_9_16(9, 16),
    ;

    val isNatural: Boolean
        get() = this == NATURAL
}
