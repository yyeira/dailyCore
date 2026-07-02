package com.yyeira.dailycollage.model

data class CollageCell(
    val imageIndex: Int,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

data class CollageLayout(
    val canvasWidth: Int,
    val canvasHeight: Int,
    val cells: List<CollageCell>,
    val description: String,
)
