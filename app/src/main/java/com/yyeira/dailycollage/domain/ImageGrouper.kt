package com.yyeira.dailycollage.domain

import com.yyeira.dailycollage.model.GalleryImage
import com.yyeira.dailycollage.util.CollageLogger

object ImageGrouper {
    private const val MAX_IMAGES_PER_COLLAGE = 9

    fun groupByDay(images: List<GalleryImage>): Map<String, List<GalleryImage>> {
        return images
            .sortedBy { it.takenAtMillis }
            .groupBy { it.dateKey }
            .toSortedMap()
    }

    /**
     * 跨天合并：以「天」为单位打包，每组最多 9 张，同一天在跨天合并不拆散。
     * 若单日超过 9 张，则该日单独成一张（不拆分该日照片）。
     */
    fun groupByCrossDayMerge(images: List<GalleryImage>): List<Pair<String, List<GalleryImage>>> {
        val days = groupByDay(images).toList()
        if (days.isEmpty()) return emptyList()

        val groups = mutableListOf<List<GalleryImage>>()
        var pendingDays = mutableListOf<List<GalleryImage>>()
        var pendingCount = 0

        fun flushPending() {
            if (pendingDays.isEmpty()) return
            groups.add(
                pendingDays.flatten().sortedBy { it.takenAtMillis },
            )
            pendingDays = mutableListOf()
            pendingCount = 0
        }

        for ((_, dayImages) in days) {
            val sortedDayImages = dayImages.sortedBy { it.takenAtMillis }
            val dayCount = sortedDayImages.size

            if (dayCount > MAX_IMAGES_PER_COLLAGE) {
                flushPending()
                groups.add(sortedDayImages)
                continue
            }

            if (pendingCount + dayCount <= MAX_IMAGES_PER_COLLAGE) {
                pendingDays.add(sortedDayImages)
                pendingCount += dayCount
            } else {
                flushPending()
                pendingDays.add(sortedDayImages)
                pendingCount = dayCount
            }
        }
        flushPending()

        val result = groups.mapIndexed { index, chunk ->
            groupKeyFor(chunk, index) to chunk.sortedBy { it.takenAtMillis }
        }
        CollageLogger.i(
            "crossDayMerge input=${images.size} days=${days.size} groups=${result.size} " +
                result.joinToString { (key, chunk) ->
                    "$key(${chunk.size} imgs, dates=${chunk.map { it.dateKey }.distinct().size})"
                },
        )
        return result
    }

    private fun groupKeyFor(chunk: List<GalleryImage>, index: Int): String {
        val dates = chunk.map { it.dateKey }.distinct().sorted()
        val base = when {
            dates.isEmpty() -> "group"
            dates.size == 1 -> dates.first()
            else -> "${dates.first()} ~ ${dates.last()}"
        }
        return if (index == 0) base else "$base ($index)"
    }
}
