package com.yyeira.dailycollage.domain

import com.yyeira.dailycollage.model.GalleryImage

object ImageGrouper {
    fun groupByDay(images: List<GalleryImage>): Map<String, List<GalleryImage>> {
        return images
            .groupBy { it.dateKey }
            .toSortedMap()
    }

    /** 按时间顺序每 9 张一组，可跨天。 */
    fun groupByNine(images: List<GalleryImage>): List<Pair<String, List<GalleryImage>>> {
        val sorted = images.sortedBy { it.takenAtMillis }
        return sorted.chunked(9).mapIndexed { index, chunk ->
            groupKeyFor(chunk, index) to chunk
        }
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
