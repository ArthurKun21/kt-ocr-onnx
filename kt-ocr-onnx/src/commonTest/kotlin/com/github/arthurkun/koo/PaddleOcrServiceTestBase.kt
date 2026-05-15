package com.github.arthurkun.koo

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import com.github.arthurkun.koo.recognition.RecognitionModel
import com.github.arthurkun.koo.recognition.RecognitionModelCachePolicy
import com.github.arthurkun.koo.recognition.base.BaseRecognitionModel
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

    protected open fun createPaddleOcrService(
        recognitionModel: RecognitionModel = BaseRecognitionModel,
        recognitionModelCachePolicy: RecognitionModelCachePolicy = RecognitionModelCachePolicy.KEEP_IN_MEMORY,
    ): OcrApi {
        return PaddleOcrService(
            recognitionModel = recognitionModel,
            recognitionModelCachePolicy = recognitionModelCachePolicy,
        )
    }

    protected lateinit var paddleOcrService: OcrApi

    @BeforeTest
    open fun setUp() {
        paddleOcrService = createPaddleOcrService()
    }

    @AfterTest
    open fun tearDown() {
        paddleOcrService.close()
    }

    @Test
    fun testDetectAndRecognizeTextFromTestImage() = runTest {
        val bytes = loadTestResourceBytes(TEST_IMAGE_PATH)
        val results = paddleOcrService.detectAndRecognizeText(bytes)
        assertRecognizedTextMatchesBaseline(results)
    }

    @Test
    fun testDetectAndRecognizeTextKeepsExplicitRecognitionSessionInMemory() = runTest {
        val bytes = loadTestResourceBytes(TEST_IMAGE_PATH)
        val recognitionModel = CountingRecognitionModel()

        val firstResults = paddleOcrService.detectAndRecognizeText(bytes, recognitionModel)
        val secondResults = paddleOcrService.detectAndRecognizeText(bytes, recognitionModel)

        assertRecognizedTextMatchesBaseline(firstResults)
        assertRecognizedTextMatchesBaseline(secondResults)
        assertThat(recognitionModel.modelLoadCount).isEqualTo(1)
        assertThat(recognitionModel.dictionaryLoadCount).isEqualTo(1)
    }

    @Test
    fun testDetectAndRecognizeTextLoadEachTimeCreatesNewRecognitionSession() = runTest {
        val bytes = loadTestResourceBytes(TEST_IMAGE_PATH)
        val recognitionModel = CountingRecognitionModel()
        val localService = createPaddleOcrService(
            recognitionModel = recognitionModel,
            recognitionModelCachePolicy = RecognitionModelCachePolicy.LOAD_EACH_TIME,
        )

        try {
            val firstResults = localService.detectAndRecognizeText(bytes, recognitionModel)
            val secondResults = localService.detectAndRecognizeText(bytes, recognitionModel)

            assertRecognizedTextMatchesBaseline(firstResults)
            assertRecognizedTextMatchesBaseline(secondResults)
        } finally {
            localService.close()
        }

        assertThat(recognitionModel.modelLoadCount).isEqualTo(2)
        assertThat(recognitionModel.dictionaryLoadCount).isEqualTo(2)
    }

    @Test
    fun testCloseRejectsFurtherRecognitionRequests() = runTest {
        val bytes = loadTestResourceBytes(TEST_IMAGE_PATH)
        val recognitionModel = CountingRecognitionModel()
        val localService = createPaddleOcrService(recognitionModel = recognitionModel)

        val results = localService.detectAndRecognizeText(bytes, recognitionModel)
        assertRecognizedTextMatchesBaseline(results)

        localService.close()

        assertFailsWith<OCRClosedException> {
            localService.detectAndRecognizeText(bytes, recognitionModel)
        }
        assertThat(recognitionModel.modelLoadCount).isEqualTo(1)
        assertThat(recognitionModel.dictionaryLoadCount).isEqualTo(1)
    }

    @Test
    fun testDetectAndRecognizeTextInvalidBytesThrows() = runTest {
        val invalidBytes = byteArrayOf(0x00, 0x11, 0x22, 0x33)

        assertFailsWith<OCRImageDecodeException> {
            paddleOcrService.detectAndRecognizeText(invalidBytes)
        }
    }

    protected fun assertRecognizedTextMatchesBaseline(results: List<OcrResult>) {
        assertThat(results).isNotEmpty()
        val combinedText = results.joinToString(" ") { it.text }
        val normalized = combinedText.replace(Regex("\\s+"), " ").trim()
        assertThat(normalized).isNotEmpty()
        assertThat(normalized).contains("Gate of Skye")
        assertThat(normalized).contains("Lv")
    }
}

internal class CountingRecognitionModel(
    override val id: String = "counting-base-recognition-model",
    private val delegate: RecognitionModel = BaseRecognitionModel,
) : RecognitionModel {
    var modelLoadCount: Int = 0
        private set

    var dictionaryLoadCount: Int = 0
        private set

    override suspend fun loadModelBytes(): ByteArray {
        modelLoadCount += 1
        return delegate.loadModelBytes()
    }

    override suspend fun loadDictionaryBytes(): ByteArray {
        dictionaryLoadCount += 1
        return delegate.loadDictionaryBytes()
    }
}

internal const val TEST_IMAGE_PATH = "ocr/noble-phantasm-en.png"
