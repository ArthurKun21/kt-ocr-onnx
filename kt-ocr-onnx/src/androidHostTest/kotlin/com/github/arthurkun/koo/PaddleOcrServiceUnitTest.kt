package com.github.arthurkun.koo

import kotlin.test.Test
import kotlin.test.assertEquals

class PaddleOcrServiceUnitTest {

    @Test
    fun testSortedForReadingOrderPrefersTopThenLeft() {
        val boxes = listOf(
            box(left = 200, top = 8),
            box(left = 20, top = 6),
            box(left = 10, top = 48),
        )

        val sorted = boxes.sortedForReadingOrder()

        val minXs = sorted.map { result -> result.points.minOf { point -> point.x } }
        assertEquals(listOf(20, 200, 10), minXs)
    }

    private fun box(left: Int, top: Int): DetectedResults {
        val width = 60
        val height = 20
        return DetectedResults(
            points = listOf(
                BoxPoint(left, top),
                BoxPoint(left + width, top),
                BoxPoint(left + width, top + height),
                BoxPoint(left, top + height),
            ),
            score = 0.9f,
        )
    }
}
