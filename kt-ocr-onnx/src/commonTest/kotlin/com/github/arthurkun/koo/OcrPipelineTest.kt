package com.github.arthurkun.koo

import com.github.arthurkun.koo.imaging.CvImage
import kotlin.test.Test
import kotlin.test.assertEquals

class OcrPipelineTest {

    @Test
    fun testSortedForReadingOrderGroupsRowsThenColumns() {
        val boxTopRight = box(left = 120, top = 12)
        val boxBottomLeft = box(left = 20, top = 38)
        val boxTopLeft = box(left = 10, top = 10)
        val boxBottomRight = box(left = 130, top = 40)

        val sorted = listOf(boxTopRight, boxBottomLeft, boxTopLeft, boxBottomRight).sortedForReadingOrder()

        val minXs = sorted.map { it.points.minOf { point -> point.x } }
        assertEquals(listOf(10, 120, 20, 130), minXs)
    }

    @Test
    fun testWholeImageBoxUsesImageBounds() {
        val image = FakeCvImage(width = 320, height = 180)

        val fullBox = wholeImageBox(image)

        assertEquals(4, fullBox.points.size)
        assertEquals(BoxPoint(0, 0), fullBox.points[0])
        assertEquals(BoxPoint(320, 0), fullBox.points[1])
        assertEquals(BoxPoint(320, 180), fullBox.points[2])
        assertEquals(BoxPoint(0, 180), fullBox.points[3])
        assertEquals(1.0f, fullBox.score)
    }

    private fun box(left: Int, top: Int): DetectedResults {
        val width = 40
        val height = 14
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

private class FakeCvImage(
    override val width: Int,
    override val height: Int,
) : CvImage {
    override val tag: String = "fake"

    override fun isEmpty(): Boolean = false

    override fun resizeTo(targetHeight: Int, targetWidth: Int): CvImage {
        throw UnsupportedOperationException("Not needed for this test")
    }

    override fun toRgbCvImage(): CvImage {
        throw UnsupportedOperationException("Not needed for this test")
    }

    override fun getPixel(y: Int, x: Int): DoubleArray {
        throw UnsupportedOperationException("Not needed for this test")
    }

    override fun convertToFloat(): CvImage {
        throw UnsupportedOperationException("Not needed for this test")
    }

    override fun close() {
    }
}
