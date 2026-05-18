package com.github.arthurkun.koo

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.arthurkun.koo.imaging.NativeMat
import com.github.arthurkun.koo.recognition.RecognitionModel
import com.github.arthurkun.koo.recognition.RecognitionModelCachePolicy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * JVM tests for PaddleOcrRecognitionService.
 *
 * Tests the recognition-only wrapper using shared test assets.
 * Extends [PaddleOcrRecognitionServiceTestBase] for shared test logic.
 * Test assets are loaded from the JVM classpath resources.
 */
class PaddleOcrRecognitionServiceJvmTest : PaddleOcrRecognitionServiceTestBase() {

    override fun createPaddleOcrRecognitionService(
        recognitionModel: RecognitionModel,
        recognitionModelCachePolicy: RecognitionModelCachePolicy,
    ): RecognitionApi {
        return PaddleOcrRecognitionService(
            recognitionModel = recognitionModel,
            recognitionModelCachePolicy = recognitionModelCachePolicy,
        )
    }

    override fun loadTestResourceBytes(path: String): ByteArray {
        val inputStream = requireNotNull(
            Thread.currentThread().contextClassLoader?.getResourceAsStream(path),
        ) {
            "Test resource not found: $path"
        }

        return inputStream.readBytes()
    }

    @Test
    fun testRecognizeTextMatUsesDefaultRecognitionModel() = runTest {
        val bytes = loadTestResourceBytes(TEST_IMAGE_PATH)
        val image = NativeMat.fromByteArray(bytes, isColor = true, tag = "jvm-test")

        try {
            val result = (paddleOcrRecognitionService as JvmRecognitionApi).recognizeText(image.mat)
            assertRecognizedTextMatchesBaseline(result)
        } finally {
            image.close()
        }
    }

    @Test
    fun testRecognizeTextMatUsesExplicitRecognitionSession() = runTest {
        val bytes = loadTestResourceBytes(TEST_IMAGE_PATH)
        val recognitionModel = CountingRecognitionModel()
        val image = NativeMat.fromByteArray(bytes, isColor = true, tag = "jvm-test")

        try {
            val result = (paddleOcrRecognitionService as JvmRecognitionApi).recognizeText(image.mat, recognitionModel)
            assertRecognizedTextMatchesBaseline(result)
            assertThat(recognitionModel.modelLoadCount).isEqualTo(1)
            assertThat(recognitionModel.dictionaryLoadCount).isEqualTo(1)
        } finally {
            image.close()
        }
    }
}
