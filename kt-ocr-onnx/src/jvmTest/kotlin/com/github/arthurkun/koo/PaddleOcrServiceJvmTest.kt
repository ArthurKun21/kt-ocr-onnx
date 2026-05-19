package com.github.arthurkun.koo

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.arthurkun.koo.imaging.NativeMat
import com.github.arthurkun.koo.recognition.RecognitionModel
import com.github.arthurkun.koo.recognition.RecognitionModelCachePolicy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(InternalKtOcrONNXApi::class)
class PaddleOcrServiceJvmTest : PaddleOcrServiceTestBase() {

    override fun createPaddleOcrService(
        recognitionModel: RecognitionModel,
        recognitionModelCachePolicy: RecognitionModelCachePolicy,
    ): OcrApi {
        return PaddleOcrService(
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
    fun testDetectAndRecognizeTextMatUsesDefaultRecognitionModel() = runTest {
        val bytes = loadTestResourceBytes(TEST_IMAGE_PATH)
        val image = NativeMat.fromByteArray(bytes, isColor = true, tag = "jvm-test")

        try {
            val results = (paddleOcrService as JvmOcrApi).detectAndRecognizeText(image.mat)
            assertRecognizedTextMatchesBaseline(results)
        } finally {
            image.close()
        }
    }

    @Test
    fun testDetectAndRecognizeTextMatUsesExplicitRecognitionSession() = runTest {
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
