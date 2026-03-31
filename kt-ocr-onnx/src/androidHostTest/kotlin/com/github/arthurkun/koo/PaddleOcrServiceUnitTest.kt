package com.github.arthurkun.koo

import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for PaddleOcrService helper functions and CTC decoding.
 *
 * These tests verify the core logic without requiring Android context.
 */
class PaddleOcrServiceUnitTest {

    @Test
    fun testCtcDecodeBasic() {
        // Given: A mock output with clear character predictions
        // Simulating output shape [1, seq_len, num_classes]
        val output = arrayOf(
            arrayOf(
                floatArrayOf(0.1f, 0.9f, 0.0f, 0.0f), // 'a' (index 1)
                floatArrayOf(0.1f, 0.9f, 0.0f, 0.0f), // 'a' duplicate - should be skipped
                floatArrayOf(0.1f, 0.0f, 0.9f, 0.0f), // 'b' (index 2)
                floatArrayOf(0.9f, 0.0f, 0.0f, 0.1f), // blank (index 0) - should be skipped
                floatArrayOf(0.1f, 0.0f, 0.0f, 0.9f), // 'c' (index 3)
            ),
        )

        val dictionary = mapOf(
            0 to "blank",
            1 to "a",
            2 to "b",
            3 to "c",
        )

        // When: CTC decode
        val result = ctcDecodeTest(output, dictionary)

        // Then: Should return "abc" (duplicates and blanks removed)
        assertEquals("abc", result)
    }

    @Test
    fun testCtcDecodeWithConsecutiveDuplicates() {
        // Given: Output with consecutive duplicates separated by blanks
        val output = arrayOf(
            arrayOf(
                floatArrayOf(0.1f, 0.9f, 0.0f), // 'a'
                floatArrayOf(0.9f, 0.1f, 0.0f), // blank
                floatArrayOf(0.1f, 0.9f, 0.0f), // 'a' - should be included (separated by blank)
            ),
        )

        val dictionary = mapOf(
            0 to "blank",
            1 to "a",
            2 to "b",
        )

        // When: CTC decode
        val result = ctcDecodeTest(output, dictionary)

        // Then: Should return "aa" (blank separates duplicates)
        assertEquals("aa", result)
    }

    @Test
    fun testCtcDecodeEmptyOutput() {
        // Given: Empty output
        val output = arrayOf(arrayOf<FloatArray>())

        val dictionary = mapOf(0 to "blank", 1 to "a")

        // When: CTC decode
        val result = ctcDecodeTest(output, dictionary)

        // Then: Should return empty string
        assertEquals("", result)
    }

    @Test
    fun testCtcDecodeAllBlanks() {
        // Given: All blank predictions
        val output = arrayOf(
            arrayOf(
                floatArrayOf(0.9f, 0.1f),
                floatArrayOf(0.9f, 0.1f),
                floatArrayOf(0.9f, 0.1f),
            ),
        )

        val dictionary = mapOf(0 to "blank", 1 to "a")

        // When: CTC decode
        val result = ctcDecodeTest(output, dictionary)

        // Then: Should return empty string
        assertEquals("", result)
    }

    @Test
    fun testFilterNumbersOnly() {
        // Given: A text string with mixed content
        val text = "Gate of Skye Lv. 1"

        // When: Filter for digits only
        val numberText = text.filter { it.isDigit() }

        // Then: Should return only "1"
        assertEquals("1", numberText)
    }

    @Test
    fun testFilterWithWhitelist() {
        // Given: A text string and whitelist
        val text = "Level 42 Stage 3"
        val whitelist = "0123456789"

        // When: Filter based on whitelist
        val filtered = text.filter { it in whitelist }

        // Then: Should return "423"
        assertEquals("423", filtered)
    }

    @Test
    fun testFilterWithBlacklist() {
        // Given: A text string and blacklist
        val text = "AB123CD"
        val blacklist = "ABCD"

        // When: Filter based on blacklist
        val filtered = text.filterNot { it in blacklist }

        // Then: Should return "123"
        assertEquals("123", filtered)
    }

    @Test
    fun testDictionaryLoading() {
        // Given: A sample dictionary content
        val lines = listOf("一", "乙", "二", "十")

        // When: Building dictionary map
        val dictionary = mutableMapOf<Int, String>()
        dictionary[0] = "blank"
        var index = 1
        for (line in lines) {
            val char = line.trim()
            if (char.isNotEmpty()) {
                dictionary[index] = char
                index++
            }
        }

        // Then: Dictionary should have correct mappings
        assertEquals(5, dictionary.size)
        assertEquals("blank", dictionary[0])
        assertEquals("一", dictionary[1])
        assertEquals("乙", dictionary[2])
        assertEquals("二", dictionary[3])
        assertEquals("十", dictionary[4])
    }

    @Test
    fun testAspectRatioCalculation() {
        // Given: Image dimensions
        val width = 640
        val height = 48
        val targetHeight = 48
        val targetWidth = 320

        // When: Calculate resize width
        val ratio = width.toDouble() / height.toDouble()
        val resizedW = if (ceil(targetHeight * ratio).toInt() > targetWidth) {
            targetWidth
        } else {
            ceil(targetHeight * ratio).toInt()
        }

        // Then: Should cap at target width
        assertEquals(320, resizedW)
    }

    @Test
    fun testAspectRatioCalculationNarrowImage() {
        // Given: Narrow image dimensions
        val width = 100
        val height = 50
        val targetHeight = 48
        val targetWidth = 320

        // When: Calculate resize width
        val ratio = width.toDouble() / height.toDouble()
        val resizedW = if (ceil(targetHeight * ratio).toInt() > targetWidth) {
            targetWidth
        } else {
            ceil(targetHeight * ratio).toInt()
        }

        // Then: Should maintain aspect ratio
        assertEquals(96, resizedW)
    }

    @Test
    fun testNormalizationFormula() {
        // Given: A pixel value
        val pixelValue = 128.0f

        // When: Normalize using PaddleOCR formula
        val normalized = (pixelValue / 255.0f - 0.5f) / 0.5f

        // Then: Should be close to 0 for mid-gray
        assertTrue(normalized > -0.1f && normalized < 0.1f)
    }

    @Test
    fun testNormalizationBounds() {
        // Given: Min and max pixel values
        val minPixel = 0.0f
        val maxPixel = 255.0f

        // When: Normalize
        val minNorm = (minPixel / 255.0f - 0.5f) / 0.5f
        val maxNorm = (maxPixel / 255.0f - 0.5f) / 0.5f

        // Then: Should be in [-1, 1] range
        assertEquals(-1.0f, minNorm, 0.001f)
        assertEquals(1.0f, maxNorm, 0.001f)
    }

    /**
     * Helper function to test CTC decoding logic.
     * Mirrors the ctcDecode function in PaddleOcrService.
     */
    private fun ctcDecodeTest(output: Array<Array<FloatArray>>, dictionary: Map<Int, String>): String {
        val charList = mutableListOf<String>()

        if (output.isEmpty() || output[0].isEmpty()) {
            return ""
        }

        val batchOutput = output[0]
        var prevIdx = -1

        for (timestep in batchOutput) {
            if (timestep.isEmpty()) continue

            var maxIdx = 0
            var maxVal = timestep[0]
            for (i in 1 until timestep.size) {
                if (timestep[i] > maxVal) {
                    maxVal = timestep[i]
                    maxIdx = i
                }
            }

            if (maxIdx != prevIdx && maxIdx != 0) {
                dictionary[maxIdx]?.let { char ->
                    charList.add(char)
                }
            }

            prevIdx = maxIdx
        }

        return charList.joinToString("")
    }
}
