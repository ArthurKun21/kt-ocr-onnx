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
 * Abstract base class for PaddleOcrRecognitionService tests.
 *
 * Provides shared test logic for both JVM and Android device tests.
 * Each platform implements [loadTestResourceBytes] to load test assets
 * from the appropriate location.
 */
@OptIn(InternalKtOcrONNXApi::class)
abstract class PaddleOcrRecognitionServiceTestBase {

    abstract fun loadTestResourceBytes(path: String): ByteArray

    abstract fun listTestResourceDirectories(path: String): List<String>

    protected abstract fun createPaddleOcrRecognitionService(
        recognitionModel: RecognitionModel = BaseRecognitionModel,
        recognitionModelCachePolicy: RecognitionModelCachePolicy = RecognitionModelCachePolicy.KEEP_IN_MEMORY,
    ): RecognitionApi

    protected lateinit var paddleOcrRecognitionService: RecognitionApi

    @BeforeTest
    open fun setUp() {
        initOpenCV()
        paddleOcrRecognitionService = createPaddleOcrRecognitionService()
    }

    @AfterTest
    open fun tearDown() {
        paddleOcrRecognitionService.close()
    }

    @Test
    fun testRecognizeTextFromTestImage() = runTest {
        for (testCase in loadRecognitionTestCases()) {
            val bytes = loadTestResourceBytes(testCase.imagePath)
            val result = paddleOcrRecognitionService.recognizeText(bytes)
            assertRecognizedTextMatchesBaseline(result, testCase)
        }
    }

    @Test
    fun testRecognizeTextKeepsExplicitRecognitionSessionInMemory() = runTest {
        val bytes = loadTestResourceBytes(defaultRecognitionTestCase().imagePath)
        val recognitionModel = CountingRecognitionModel()

        val firstResult = paddleOcrRecognitionService.recognizeText(bytes, recognitionModel)
        val secondResult = paddleOcrRecognitionService.recognizeText(bytes, recognitionModel)

        assertRecognizedTextMatchesBaseline(firstResult)
        assertRecognizedTextMatchesBaseline(secondResult)
        assertThat(recognitionModel.modelLoadCount).isEqualTo(1)
        assertThat(recognitionModel.dictionaryLoadCount).isEqualTo(1)
    }

    @Test
    fun testRecognizeTextLoadEachTimeCreatesNewRecognitionSession() = runTest {
        val bytes = loadTestResourceBytes(defaultRecognitionTestCase().imagePath)
        val recognitionModel = CountingRecognitionModel()
        val localService = createPaddleOcrRecognitionService(
            recognitionModel = recognitionModel,
            recognitionModelCachePolicy = RecognitionModelCachePolicy.LOAD_EACH_TIME,
        )

        try {
            val firstResult = localService.recognizeText(bytes, recognitionModel)
            val secondResult = localService.recognizeText(bytes, recognitionModel)

            assertRecognizedTextMatchesBaseline(firstResult)
            assertRecognizedTextMatchesBaseline(secondResult)
        } finally {
            localService.close()
        }

        assertThat(recognitionModel.modelLoadCount).isEqualTo(2)
        assertThat(recognitionModel.dictionaryLoadCount).isEqualTo(2)
    }

    @Test
    fun testCloseRejectsFurtherRecognitionRequests() = runTest {
        val bytes = loadTestResourceBytes(defaultRecognitionTestCase().imagePath)
        val recognitionModel = CountingRecognitionModel()
        val localService = createPaddleOcrRecognitionService(recognitionModel = recognitionModel)

        val result = localService.recognizeText(bytes, recognitionModel)
        assertRecognizedTextMatchesBaseline(result)

        localService.close()

        assertFailsWith<OCRClosedException> {
            localService.recognizeText(bytes, recognitionModel)
        }
        assertThat(recognitionModel.modelLoadCount).isEqualTo(1)
        assertThat(recognitionModel.dictionaryLoadCount).isEqualTo(1)
    }

    @Test
    fun testRecognizeTextInvalidBytesThrows() = runTest {
        val invalidBytes = byteArrayOf(0x00, 0x11, 0x22, 0x33)

        assertFailsWith<OCRImageDecodeException> {
            paddleOcrRecognitionService.recognizeText(invalidBytes)
        }
    }

    protected fun assertRecognizedTextMatchesBaseline(
        result: RecognitionResult,
        testCase: RecognitionTestImageCase = defaultRecognitionTestCase(),
    ) {
        val normalized = result.text.replace(Regex("\\s+"), " ").trim()
        assertThat(normalized).isNotEmpty()
        testCase.expectedTextLines.forEach { expectedText ->
            assertThat(normalized).contains(expectedText)
        }
    }

    protected fun loadRecognitionTestCases(): List<RecognitionTestImageCase> {
        val cases = listTestResourceDirectories(OCR_TEST_CASES_PATH).map { casePath ->
            RecognitionTestImageCase(
                directoryPath = casePath,
                imagePath = "$casePath/image.png",
                expectedTextLines = loadOptionalTextLines("$casePath/text.txt"),
            )
        }
        assertThat(cases).isNotEmpty()
        return cases
    }

    protected fun defaultRecognitionTestCase(): RecognitionTestImageCase = loadRecognitionTestCases().first()

    private fun loadOptionalTextLines(path: String): List<String> {
        val text = runCatching { loadTestResourceBytes(path).decodeToString() }.getOrNull()
        val lines = text
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toList()
            .orEmpty()
        return lines.ifEmpty { DEFAULT_EXPECTED_TEXT_LINES }
    }
}

data class RecognitionTestImageCase(
    val directoryPath: String,
    val imagePath: String,
    val expectedTextLines: List<String>,
)

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

internal const val OCR_TEST_CASES_PATH = "ocr"
internal val DEFAULT_EXPECTED_TEXT_LINES: List<String> = listOf("Gate of Skye", "Lv")
