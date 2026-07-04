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
            LayoutRule.HERO_LEFT -> planHeroSide(sizedImages, leftHero = true)
            LayoutRule.HERO_RIGHT -> planHeroSide(sizedImages, leftHero = false)
            LayoutRule.GRID_2 -> rowPackLayout(sizedImages, 2, "layout_auto_row_2")
            LayoutRule.GRID_3 -> rowPackLayout(sizedImages, 3, "layout_auto_row_3")
            LayoutRule.GRID_4 -> rowPackLayout(sizedImages, 4, "layout_auto_row_4")
            LayoutRule.GRID_SQUARE -> gridSquareLayout(sizedImages, guessSquareColumns(sizedImages.size), "layout_grid_square")
            LayoutRule.FIT_2 -> proportionalRowPackLayout(sizedImages, 2, "layout_fit_2")
            LayoutRule.FIT_3 -> proportionalRowPackLayout(sizedImages, 3, "layout_fit_3")
            LayoutRule.FIT_4 -> proportionalRowPackLayout(sizedImages, 4, "layout_fit_4")
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

    private fun planHeroSide(sizedImages: List<SizedImage>, leftHero: Boolean): CollageLayout {
        val description = if (leftHero) "layout_hero_left" else "layout_hero_right"
        return when (sizedImages.size) {
            1 -> singleLayout(sizedImages)
            2 -> horizontalProportionalRow(sizedImages, description)
            3 -> if (leftHero) {
                leftBigRightStackLayout(sizedImages, description)
            } else {
                rightBigLeftStackLayout(sizedImages, description)
            }
            else -> if (leftHero) {
                heroSideWithStackLayout(sizedImages, heroFirst = true, description)
            } else {
                heroSideWithStackLayout(sizedImages, heroFirst = false, description)
            }
        }
    }

    private fun heroSideWithStackLayout(
        sizedImages: List<SizedImage>,
        heroFirst: Boolean,
        description: String,
    ): CollageLayout {
        val heroImage = if (heroFirst) sizedImages.first() else sizedImages.last()
        val stackImages = if (heroFirst) sizedImages.drop(1) else sizedImages.dropLast(1)

        val heroWidth = ((canvasWidth - GAP) * 3) / 5
        val stackWidth = canvasWidth - GAP - heroWidth

        val stackLayout = verticalStackInWidth(stackImages, stackWidth, if (heroFirst) 1 else 0)
        val heroHeight = max(stackLayout.canvasHeight, fitHeight(heroWidth, heroImage.dimensions.aspectRatio))

        val heroIndex = if (heroFirst) 0 else sizedImages.lastIndex
        val heroLeft = if (heroFirst) 0 else stackWidth + GAP
        val stackLeft = if (heroFirst) heroWidth + GAP else 0

        val cells = buildList {
            add(CollageCell(heroIndex, heroLeft, 0, heroWidth, heroHeight))
            stackLayout.cells.forEach { cell ->
                add(cell.copy(left = cell.left + stackLeft))
            }
        }

        return CollageLayout(
            canvasWidth = canvasWidth,
            canvasHeight = heroHeight,
            cells = cells,
            description = description,
        )
    }

    private fun verticalStackInWidth(
        sizedImages: List<SizedImage>,
        width: Int,
        startIndex: Int,
    ): CollageLayout {
        var top = 0
        val cells = sizedImages.mapIndexed { i, sizedImage ->
            val slotHeight = fitHeight(width, sizedImage.dimensions.aspectRatio)
            val cell = CollageCell(
                imageIndex = startIndex + i,
                left = 0,
                top = top,
                width = width,
                height = slotHeight,
            )
            top += slotHeight
            if (i < sizedImages.lastIndex) top += GAP
            cell
        }
        return CollageLayout(canvasWidth = width, canvasHeight = top, cells = cells, description = "")
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
                proportionalRowPackLayout(sizedImages, 2, "layout_fit_2"),
                gridSquareLayout(sizedImages, 2, "layout_grid_2x2"),
                rowPackLayout(sizedImages, 2, "layout_auto_row_2"),
                heroSideWithStackLayout(sizedImages, heroFirst = true, "layout_hero_left"),
            )
            5, 6 -> listOf(
                proportionalRowPackLayout(sizedImages, 2, "layout_fit_2"),
                proportionalRowPackLayout(sizedImages, 3, "layout_fit_3"),
                rowPackLayout(sizedImages, 2, "layout_auto_row_2"),
                rowPackLayout(sizedImages, 3, "layout_auto_row_3"),
            )
            else -> buildList {
                add(proportionalRowPackLayout(sizedImages, 2, "layout_fit_2"))
                add(proportionalRowPackLayout(sizedImages, 3, "layout_fit_3"))
                add(rowPackLayout(sizedImages, 2, "layout_auto_row_2"))
                add(rowPackLayout(sizedImages, 3, "layout_auto_row_3"))
                if (sizedImages.size <= 16) {
                    add(proportionalRowPackLayout(sizedImages, 4, "layout_fit_4"))
                }
            }
        }
    }

    private fun buildTwoImageCandidates(sizedImages: List<SizedImage>): List<CollageLayout> {
        return listOf(
            horizontalProportionalRow(sizedImages, "layout_fit_2"),
            verticalStackLayout(sizedImages, "layout_vertical"),
            horizontalRowLayout(sizedImages, "layout_horizontal"),
        )
    }

    private fun buildThreeImageCandidates(sizedImages: List<SizedImage>): List<CollageLayout> {
        return listOf(
            proportionalRowPackLayout(sizedImages, 3, "layout_fit_3"),
            leftBigRightStackLayout(sizedImages, "layout_left_big"),
            rightBigLeftStackLayout(sizedImages, "layout_hero_right"),
            heroTopLayout(sizedImages, "layout_hero_top"),
            verticalStackLayout(sizedImages, "layout_vertical"),
            horizontalProportionalRow(sizedImages, "layout_horizontal"),
        )
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

    /** 右大图 + 左侧上下堆叠 */
    private fun rightBigLeftStackLayout(sizedImages: List<SizedImage>, description: String): CollageLayout {
        require(sizedImages.size == 3)

        val rightWidth = ((canvasWidth - GAP) * 2) / 3
        val leftWidth = canvasWidth - GAP - rightWidth

        val rightHeight = fitHeight(rightWidth, sizedImages[2].dimensions.aspectRatio)
        val leftTopHeight = fitHeight(leftWidth, sizedImages[0].dimensions.aspectRatio)
        val leftBottomHeight = fitHeight(leftWidth, sizedImages[1].dimensions.aspectRatio)
        val leftStackHeight = leftTopHeight + GAP + leftBottomHeight
        val canvasHeight = max(rightHeight, leftStackHeight)

        val cells = listOf(
            CollageCell(0, 0, 0, leftWidth, leftTopHeight),
            CollageCell(1, 0, leftTopHeight + GAP, leftWidth, leftBottomHeight),
            CollageCell(2, leftWidth + GAP, 0, rightWidth, canvasHeight),
        )

        return CollageLayout(
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            cells = cells,
            description = description,
        )
    }

    /** 横向一行，按实际宽高比分配宽度，同行等高 */
    private fun horizontalProportionalRow(sizedImages: List<SizedImage>, description: String): CollageLayout {
        val totalGap = GAP * (sizedImages.size - 1)
        val availableWidth = canvasWidth - totalGap
        val totalAspect = sizedImages.sumOf { it.dimensions.aspectRatio.toDouble() }.toFloat()
        val rowHeight = (availableWidth / totalAspect).toInt().coerceAtLeast(1)

        var left = 0
        val cells = sizedImages.mapIndexed { index, sized ->
            val slotWidth = if (index == sizedImages.lastIndex) {
                canvasWidth - left
            } else {
                (availableWidth * sized.dimensions.aspectRatio / totalAspect).toInt()
            }
            val cell = CollageCell(index, left, 0, slotWidth, rowHeight)
            left += slotWidth + GAP
            cell
        }

        return CollageLayout(
            canvasWidth = canvasWidth,
            canvasHeight = rowHeight,
            cells = cells,
            description = description,
        )
    }

    /** 等比例行布局：每行按图片实际宽高比分配宽度，同行所有图片等高，最大程度减少裁切 */
    private fun proportionalRowPackLayout(
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
            val totalGap = GAP * (rowImages.size - 1)
            val availableWidth = canvasWidth - totalGap
            val totalAspect = rowImages.sumOf { it.dimensions.aspectRatio.toDouble() }.toFloat()
            val rowHeight = (availableWidth / totalAspect).toInt().coerceAtLeast(1)

            var left = 0
            rowImages.forEachIndexed { col, sized ->
                val slotWidth = if (col == rowImages.lastIndex) {
                    canvasWidth - left
                } else {
                    (availableWidth * sized.dimensions.aspectRatio / totalAspect).toInt()
                }
                cells.add(
                    CollageCell(
                        imageIndex = start + col,
                        left = left,
                        top = topOffset,
                        width = slotWidth,
                        height = rowHeight,
                    ),
                )
                left += slotWidth + GAP
            }
            topOffset += rowHeight
            if (row < rowCount - 1) topOffset += GAP
        }

        return CollageLayout(
            canvasWidth = canvasWidth,
            canvasHeight = topOffset,
            cells = cells,
            description = description,
        )
    }

    private fun guessSquareColumns(count: Int): Int {
        return when {
            count <= 1 -> 1
            count <= 4 -> 2
            count <= 9 -> 3
            else -> 4
        }
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

        var cropPenalty = 0f
        layout.cells.forEach { cell ->
            val img = sizedImages.getOrNull(cell.imageIndex) ?: return@forEach
            val cellRatio = cell.width.toFloat() / cell.height
            val imgRatio = img.dimensions.aspectRatio
            val cropRatio = if (imgRatio > cellRatio) {
                1f - cellRatio / imgRatio
            } else if (imgRatio < cellRatio) {
                1f - imgRatio / cellRatio
            } else {
                0f
            }
            cropPenalty += cropRatio
        }
        cropPenalty = cropPenalty / sizedImages.size * totalHeight * 0.5f

        return totalHeight +
            rowHeightVariance * 0.2f +
            cropPenalty
    }

    companion object {
        private const val GAP = 6
    }
}
