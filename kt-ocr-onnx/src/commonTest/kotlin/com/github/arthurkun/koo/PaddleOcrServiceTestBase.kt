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

@OptIn(InternalKtOcrONNXApi::class)
abstract class PaddleOcrServiceTestBase {

    abstract fun loadTestResourceBytes(path: String): ByteArray

    protected abstract fun createPaddleOcrService(
        recognitionModel: RecognitionModel = BaseRecognitionModel,
        recognitionModelCachePolicy: RecognitionModelCachePolicy = RecognitionModelCachePolicy.KEEP_IN_MEMORY,
    ): OcrApi

    protected lateinit var paddleOcrService: OcrApi

    @BeforeTest
    open fun setUp() {
        initOpenCV()
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
