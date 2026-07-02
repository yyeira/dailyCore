package com.yyeira.dailycollage.domain

import com.yyeira.dailycollage.model.GalleryImage

object ImageGrouper {
    fun groupByDay(images: List<GalleryImage>): Map<String, List<GalleryImage>> {
        return images
            .groupBy { it.dateKey }
            .toSortedMap()
    }
}
