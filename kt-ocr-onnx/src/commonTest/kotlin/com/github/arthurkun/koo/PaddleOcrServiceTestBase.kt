package com.github.arthurkun.koo

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isNotEmpty
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Abstract base class for PaddleOcrService tests.
 *
 * Provides shared test logic for both JVM and Android device tests.
 * Each platform implements [loadTestResourceBytes] to load test assets
 * from the appropriate location.
 */
abstract class PaddleOcrServiceTestBase {

    /**
     * Loads test resource bytes from the platform-specific resource location.
     *
     * @param path The relative path to the test resource (e.g., "ocr/noble-phantasm-en.png")
     * @return The raw bytes of the resource
     */
    abstract fun loadTestResourceBytes(path: String): ByteArray

    protected lateinit var paddleOcrService: OcrApi

    @BeforeTest
    open fun setUp() {
        paddleOcrService = PaddleOcrService()
    }

    @AfterTest
    open fun tearDown() {
        paddleOcrService.close()
    }

    @Test
    fun testDetectAndRecognizeTextFromTestImage() = runTest {
        val bytes = loadTestResourceBytes("ocr/noble-phantasm-en.png")
        val results = paddleOcrService.detectAndRecognizeText(bytes)
        assertThat(results).isNotEmpty()
        val combinedText = results.joinToString(" ") { it.text }
        val normalized = combinedText.replace(Regex("\\s+"), " ").trim()
        assertThat(normalized).isNotEmpty()
        assertThat(normalized).contains("Gate of Skye")
        assertThat(normalized).contains("Lv")
    }

    @Test
    fun testDetectAndRecognizeTextInvalidBytesThrows() = runTest {
        val invalidBytes = byteArrayOf(0x00, 0x11, 0x22, 0x33)

        assertFailsWith<OCRImageDecodeException> {
            paddleOcrService.detectAndRecognizeText(invalidBytes)
        }
    }
}
