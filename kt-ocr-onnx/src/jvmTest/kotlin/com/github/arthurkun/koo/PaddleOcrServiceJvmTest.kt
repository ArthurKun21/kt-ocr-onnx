package com.github.arthurkun.koo

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.arthurkun.koo.imaging.NativeMat
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * JVM tests for PaddleOcrService.
 *
 * Tests the PaddleOCR v5 recognition model using shared test assets.
 * Extends [PaddleOcrServiceTestBase] for shared test logic.
 * Test assets are loaded from the JVM classpath resources.
 */
class PaddleOcrServiceJvmTest : PaddleOcrServiceTestBase() {

    override fun loadTestResourceBytes(path: String): ByteArray {
        val inputStream = requireNotNull(
            Thread.currentThread().contextClassLoader?.getResourceAsStream(path),
        ) {
            "Test resource not found: $path"
        }

        return inputStream.readBytes()
    }

    @Test
    fun testDetectAndRecognizeTextMatUsesExplicitRecognitionModel() = runTest {
        val bytes = loadTestResourceBytes(TEST_IMAGE_PATH)
        val recognitionModel = CountingRecognitionModel()
        val image = NativeMat.fromByteArray(bytes, isColor = true, tag = "jvm-test")

        try {
            val results = (paddleOcrService as JvmOcrApi).detectAndRecognizeText(image.mat, recognitionModel)
            assertRecognizedTextMatchesBaseline(results)
            assertThat(recognitionModel.modelLoadCount).isEqualTo(1)
            assertThat(recognitionModel.dictionaryLoadCount).isEqualTo(1)
        } finally {
            image.close()
        }
    }
}
