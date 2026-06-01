package com.github.arthurkun.koo

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.github.arthurkun.koo.imaging.NativeMat
import com.github.arthurkun.koo.recognition.RecognitionModel
import com.github.arthurkun.koo.recognition.RecognitionModelCachePolicy
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test

/**
 * JVM tests for PaddleOcrRecognitionService.
 *
 * Tests the recognition-only wrapper using shared test assets.
 * Extends [PaddleOcrRecognitionServiceTestBase] for shared test logic.
 * Test assets are loaded from the JVM classpath resources.
 */
@OptIn(InternalKtOcrONNXApi::class)
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
    fun testRecognizeTextMatUsesDefaultRecognitionModel() = runTest {
        val bytes = loadTestResourceBytes(defaultRecognitionTestCase().imagePath)
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
        val bytes = loadTestResourceBytes(defaultRecognitionTestCase().imagePath)
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
