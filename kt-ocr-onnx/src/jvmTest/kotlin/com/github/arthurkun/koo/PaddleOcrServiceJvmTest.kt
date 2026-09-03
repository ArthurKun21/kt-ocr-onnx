package com.github.arthurkun.koo

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.arthurkun.koo.detection.DetectionModel
import com.github.arthurkun.koo.detection.DetectionModelCachePolicy
import com.github.arthurkun.koo.imaging.NativeMat
import com.github.arthurkun.koo.recognition.RecognitionModel
import com.github.arthurkun.koo.recognition.RecognitionModelCachePolicy
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test

@OptIn(InternalKtOcrONNXApi::class)
class PaddleOcrServiceJvmTest : PaddleOcrServiceTestBase() {

    override fun createPaddleOcrService(
        recognitionModel: RecognitionModel,
        recognitionModelCachePolicy: RecognitionModelCachePolicy,
        detectionModel: DetectionModel,
        detectionModelCachePolicy: DetectionModelCachePolicy,
    ): OcrApi {
        return PaddleOcrService(
            recognitionModel = recognitionModel,
            recognitionModelCachePolicy = recognitionModelCachePolicy,
            detectionModel = detectionModel,
            detectionModelCachePolicy = detectionModelCachePolicy,
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

    override fun listTestResourceDirectories(path: String): List<String> {
        val resource = requireNotNull(Thread.currentThread().contextClassLoader?.getResource(path)) {
            "Test resource directory not found: $path"
        }
        val directory = Paths.get(resource.toURI())
        return Files.list(directory).use { paths ->
            paths
                .filter { Files.isDirectory(it) }
                .map { "$path/${it.fileName}" }
                .sorted()
                .toList()
        }
    }

    @Test
    fun testDetectAndRecognizeTextMatUsesDefaultRecognitionModel() = runTest {
        val bytes = loadTestResourceBytes(defaultOcrTestCase().imagePath)
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
        val bytes = loadTestResourceBytes(defaultOcrTestCase().imagePath)
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
