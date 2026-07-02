package com.yyeira.dailycollage.domain

import com.yyeira.dailycollage.model.CollageCell
import com.yyeira.dailycollage.model.CollageLayout
import com.yyeira.dailycollage.model.GalleryImage
import com.yyeira.dailycollage.model.ImageDimensions
import com.yyeira.dailycollage.model.LayoutRule
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

class CollageLayoutPlanner(
    private val canvasWidth: Int = 1080,
) {
    data class SizedImage(
        val image: GalleryImage,
        val dimensions: ImageDimensions,
    )

    fun plan(
        images: List<GalleryImage>,
        dimensions: List<ImageDimensions>,
        rule: LayoutRule = LayoutRule.AUTO,
    ): CollageLayout {
        require(images.isNotEmpty()) { "images must not be empty" }
        require(images.size == dimensions.size) { "images and dimensions size mismatch" }

        val sizedImages = images.zip(dimensions) { image, dim ->
            SizedImage(image, dim)
        }

        return when (rule) {
            LayoutRule.AUTO -> {
                val candidates = buildCandidates(sizedImages)
                candidates.minBy { score(it, sizedImages) }
            }
            LayoutRule.VERTICAL -> verticalStackLayout(sizedImages, "layout_vertical")
            LayoutRule.HORIZONTAL -> if (sizedImages.size == 1) {
                singleLayout(sizedImages)
            } else {
                horizontalRowLayout(sizedImages, "layout_horizontal")
            }
            LayoutRule.HERO_TOP -> planHeroTop(sizedImages)
            LayoutRule.GRID_2 -> rowPackLayout(sizedImages, 2, "layout_auto_row_2")
            LayoutRule.GRID_3 -> rowPackLayout(sizedImages, 3, "layout_auto_row_3")
            LayoutRule.GRID_4 -> rowPackLayout(sizedImages, 4, "layout_auto_row_4")
        }
    }

    private fun planHeroTop(sizedImages: List<SizedImage>): CollageLayout {
        return when (sizedImages.size) {
            1 -> singleLayout(sizedImages)
            2 -> verticalStackLayout(sizedImages, "layout_hero_top")
            3 -> heroTopLayout(sizedImages, "layout_hero_top")
            else -> heroFirstWithGridLayout(sizedImages, "layout_hero_top")
        }
    }

    private fun heroFirstWithGridLayout(sizedImages: List<SizedImage>, description: String): CollageLayout {
        val topLayout = singleLayout(listOf(sizedImages.first()))
        val remaining = sizedImages.drop(1)
        val bottomLayout = rowPackLayout(remaining, 2, description)

        val shiftedCells = buildList {
            add(topLayout.cells.first())
            bottomLayout.cells.forEach { cell ->
                add(
                    cell.copy(
                        imageIndex = cell.imageIndex + 1,
                        top = cell.top + topLayout.canvasHeight + GAP,
                    ),
                )
            }
        }

        return CollageLayout(
            canvasWidth = canvasWidth,
            canvasHeight = topLayout.canvasHeight + GAP + bottomLayout.canvasHeight,
            cells = shiftedCells,
            description = description,
        )
    }

    private fun buildCandidates(sizedImages: List<SizedImage>): List<CollageLayout> {
        return when (sizedImages.size) {
            1 -> listOf(singleLayout(sizedImages))
            2 -> buildTwoImageCandidates(sizedImages)
            3 -> buildThreeImageCandidates(sizedImages)
            4 -> listOf(
                gridSquareLayout(sizedImages, 2, "layout_grid_2x2"),
                rowPackLayout(sizedImages, 2, "layout_auto_row_2"),
            )
            5, 6 -> listOf(
                rowPackLayout(sizedImages, 2, "layout_auto_row_2"),
                rowPackLayout(sizedImages, 3, "layout_auto_row_3"),
            )
            else -> listOf(2, 3).map { cols ->
                rowPackLayout(sizedImages, cols, "layout_auto_row_$cols")
            }
        }
    }

    private fun buildTwoImageCandidates(sizedImages: List<SizedImage>): List<CollageLayout> {
        val allPortrait = sizedImages.all { it.dimensions.isPortrait }
        val allLandscape = sizedImages.all { it.dimensions.isLandscape }

        return when {
            allPortrait -> listOf(verticalStackLayout(sizedImages, "layout_vertical"))
            allLandscape -> listOf(horizontalRowLayout(sizedImages, "layout_horizontal"))
            else -> listOf(
                horizontalRowLayout(sizedImages, "layout_horizontal"),
                verticalStackLayout(sizedImages, "layout_vertical"),
            )
        }
    }

    private fun buildThreeImageCandidates(sizedImages: List<SizedImage>): List<CollageLayout> {
        val allPortrait = sizedImages.all { it.dimensions.isPortrait }
        val allLandscape = sizedImages.all { it.dimensions.isLandscape }

        return when {
            allPortrait -> listOf(
                verticalStackLayout(sizedImages, "layout_vertical"),
                leftBigRightStackLayout(sizedImages, "layout_left_big"),
            )
            allLandscape -> listOf(
                horizontalRowLayout(sizedImages, "layout_horizontal"),
                leftBigRightStackLayout(sizedImages, "layout_left_big"),
            )
            else -> listOf(
                leftBigRightStackLayout(sizedImages, "layout_left_big"),
                heroTopLayout(sizedImages, "layout_hero_top"),
                verticalStackLayout(sizedImages, "layout_vertical"),
            )
        }
    }

    private fun singleLayout(sizedImages: List<SizedImage>): CollageLayout {
        val aspect = sizedImages.first().dimensions.aspectRatio
        val height = fitHeight(canvasWidth, aspect)
        return CollageLayout(
            canvasWidth = canvasWidth,
            canvasHeight = height,
            cells = listOf(CollageCell(0, 0, 0, canvasWidth, height)),
            description = "layout_single",
        )
    }

    private fun horizontalRowLayout(sizedImages: List<SizedImage>, description: String): CollageLayout {
        val count = sizedImages.size
        val totalGap = GAP * (count - 1)
        val slotWidth = (canvasWidth - totalGap) / count
        val rowHeight = sizedImages.maxOf { fitHeight(slotWidth, it.dimensions.aspectRatio) }

        val cells = sizedImages.mapIndexed { index, _ ->
            CollageCell(
                imageIndex = index,
                left = index * (slotWidth + GAP),
                top = 0,
                width = slotWidth,
                height = rowHeight,
            )
        }

        return CollageLayout(
            canvasWidth = canvasWidth,
            canvasHeight = rowHeight,
            cells = cells,
            description = description,
        )
    }

    private fun verticalStackLayout(sizedImages: List<SizedImage>, description: String): CollageLayout {
        var top = 0
        val cells = sizedImages.mapIndexed { index, sizedImage ->
            val slotHeight = fitHeight(canvasWidth, sizedImage.dimensions.aspectRatio)
            val cell = CollageCell(
                imageIndex = index,
                left = 0,
                top = top,
                width = canvasWidth,
                height = slotHeight,
            )
            top += slotHeight
            if (index < sizedImages.lastIndex) {
                top += GAP
            }
            cell
        }

        return CollageLayout(
            canvasWidth = canvasWidth,
            canvasHeight = top,
            cells = cells,
            description = description,
        )
    }

    /** 手机图库经典：左大图 + 右侧上下两张 */
    private fun leftBigRightStackLayout(sizedImages: List<SizedImage>, description: String): CollageLayout {
        require(sizedImages.size == 3)

        val leftWidth = ((canvasWidth - GAP) * 2) / 3
        val rightWidth = canvasWidth - GAP - leftWidth

        val leftHeight = fitHeight(leftWidth, sizedImages[0].dimensions.aspectRatio)
        val rightTopHeight = fitHeight(rightWidth, sizedImages[1].dimensions.aspectRatio)
        val rightBottomHeight = fitHeight(rightWidth, sizedImages[2].dimensions.aspectRatio)
        val rightStackHeight = rightTopHeight + GAP + rightBottomHeight
        val canvasHeight = max(leftHeight, rightStackHeight)

        val cells = listOf(
            CollageCell(0, 0, 0, leftWidth, canvasHeight),
            CollageCell(1, leftWidth + GAP, 0, rightWidth, rightTopHeight),
            CollageCell(2, leftWidth + GAP, rightTopHeight + GAP, rightWidth, rightBottomHeight),
        )

        return CollageLayout(
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            cells = cells,
            description = description,
        )
    }

    /** 手机图库经典：上大下二 */
    private fun heroTopLayout(sizedImages: List<SizedImage>, description: String): CollageLayout {
        require(sizedImages.size == 3)

        val topHeight = fitHeight(canvasWidth, sizedImages[0].dimensions.aspectRatio)
        val bottomSlotWidth = (canvasWidth - GAP) / 2
        val bottomLeftHeight = fitHeight(bottomSlotWidth, sizedImages[1].dimensions.aspectRatio)
        val bottomRightHeight = fitHeight(bottomSlotWidth, sizedImages[2].dimensions.aspectRatio)
        val bottomRowHeight = max(bottomLeftHeight, bottomRightHeight)

        val cells = listOf(
            CollageCell(0, 0, 0, canvasWidth, topHeight),
            CollageCell(1, 0, topHeight + GAP, bottomSlotWidth, bottomRowHeight),
            CollageCell(2, bottomSlotWidth + GAP, topHeight + GAP, bottomSlotWidth, bottomRowHeight),
        )

        return CollageLayout(
            canvasWidth = canvasWidth,
            canvasHeight = topHeight + GAP + bottomRowHeight,
            cells = cells,
            description = description,
        )
    }

    /** 手机图库四宫格：等宽等高方格，图片 fit 居中 */
    private fun gridSquareLayout(
        sizedImages: List<SizedImage>,
        columns: Int,
        description: String,
    ): CollageLayout {
        val rows = ceil(sizedImages.size.toDouble() / columns).toInt()
        val slotSize = (canvasWidth - GAP * (columns - 1)) / columns
        val canvasHeight = slotSize * rows + GAP * (rows - 1)

        val cells = sizedImages.mapIndexed { index, _ ->
            val row = index / columns
            val col = index % columns
            CollageCell(
                imageIndex = index,
                left = col * (slotSize + GAP),
                top = row * (slotSize + GAP),
                width = slotSize,
                height = slotSize,
            )
        }

        return CollageLayout(
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            cells = cells,
            description = description,
        )
    }

    private fun rowPackLayout(
        sizedImages: List<SizedImage>,
        columns: Int,
        description: String,
    ): CollageLayout {
        val rowCount = ceil(sizedImages.size.toDouble() / columns).toInt()
        var topOffset = 0
        val cells = mutableListOf<CollageCell>()

        for (row in 0 until rowCount) {
            val start = row * columns
            val end = minOf(start + columns, sizedImages.size)
            val rowImages = sizedImages.subList(start, end)
            val colsInRow = rowImages.size
            val totalGap = GAP * (colsInRow - 1)
            val slotWidth = (canvasWidth - totalGap) / colsInRow
            val rowHeight = rowImages.maxOf { fitHeight(slotWidth, it.dimensions.aspectRatio) }

            rowImages.forEachIndexed { col, _ ->
                cells.add(
                    CollageCell(
                        imageIndex = start + col,
                        left = col * (slotWidth + GAP),
                        top = topOffset,
                        width = slotWidth,
                        height = rowHeight,
                    ),
                )
            }
            topOffset += rowHeight
            if (row < rowCount - 1) {
                topOffset += GAP
            }
        }

        return CollageLayout(
            canvasWidth = canvasWidth,
            canvasHeight = topOffset,
            cells = cells,
            description = description,
        )
    }

    private fun fitHeight(width: Int, aspectRatio: Float): Int {
        return (width / aspectRatio).toInt().coerceAtLeast(1)
    }

    private fun score(layout: CollageLayout, sizedImages: List<SizedImage>): Float {
        val totalHeight = layout.canvasHeight.toFloat()

        val rowGroups = layout.cells.groupBy { it.top }
        val rowHeights = rowGroups.values.map { row ->
            row.maxOf { it.top + it.height } - row.minOf { it.top }
        }
        val avgRowHeight = rowHeights.average().toFloat()
        val rowHeightVariance = rowHeights.sumOf { abs(it - avgRowHeight).toDouble() }.toFloat()

        val portraitCount = sizedImages.count { it.dimensions.isPortrait }
        val landscapeCount = sizedImages.count { it.dimensions.isLandscape }
        val portraitRatio = portraitCount.toFloat() / sizedImages.size
        val landscapeRatio = landscapeCount.toFloat() / sizedImages.size

        val orientationMismatchPenalty = when {
            layout.description.contains("horizontal") && portraitRatio > 0.6f -> totalHeight * 0.15f
            layout.description.contains("vertical") && landscapeRatio > 0.6f -> totalHeight * 0.15f
            else -> 0f
        }

        return totalHeight +
            rowHeightVariance * 0.2f +
            orientationMismatchPenalty
    }

    companion object {
        private const val GAP = 6
    }
}
