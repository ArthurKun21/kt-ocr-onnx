package com.github.arthurkun.koo

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import com.github.arthurkun.koo.detection.DetectionModel
import com.github.arthurkun.koo.detection.DetectionModelCachePolicy
import com.github.arthurkun.koo.detection.base.BaseDetectionModel
import com.github.arthurkun.koo.recognition.RecognitionModel
import com.github.arthurkun.koo.recognition.RecognitionModelCachePolicy
import com.github.arthurkun.koo.recognition.base.BaseRecognitionModel
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(InternalKtOcrONNXApi::class)
abstract class PaddleOcrServiceTestBase {

    abstract fun loadTestResourceBytes(path: String): ByteArray

    abstract fun listTestResourceDirectories(path: String): List<String>

    protected abstract fun createPaddleOcrService(
        recognitionModel: RecognitionModel = BaseRecognitionModel,
        recognitionModelCachePolicy: RecognitionModelCachePolicy = RecognitionModelCachePolicy.KEEP_IN_MEMORY,
        detectionModel: DetectionModel = BaseDetectionModel,
        detectionModelCachePolicy: DetectionModelCachePolicy = DetectionModelCachePolicy.KEEP_IN_MEMORY,
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
        for (testCase in loadOcrTestCases()) {
            val bytes = loadTestResourceBytes(testCase.imagePath)
            val results = paddleOcrService.detectAndRecognizeText(bytes)
            assertRecognizedTextMatchesBaseline(results, testCase)
            assertDetectedBoxesMatchBaseline(results.map { it.box }, testCase.expectedBoxes)
        }
    }

    @Test
    fun testDetectAndRecognizeTextInvalidBytesThrows() = runTest {
        val invalidBytes = byteArrayOf(0x00, 0x11, 0x22, 0x33)

        assertFailsWith<OCRImageDecodeException> {
            paddleOcrService.detectAndRecognizeText(invalidBytes)
        }
    }

    @Test
    fun testDetectTextKeepsExplicitDetectionSessionInMemory() = runTest {
        val bytes = loadTestResourceBytes(defaultOcrTestCase().imagePath)
        val detectionModel = CountingDetectionModel()

        val firstBoxes = paddleOcrService.detectText(bytes, detectionModel)
        val secondBoxes = paddleOcrService.detectText(bytes, detectionModel)

        assertThat(firstBoxes).isNotEmpty()
        assertThat(secondBoxes).isNotEmpty()
        assertThat(detectionModel.modelLoadCount).isEqualTo(1)
    }

    @Test
    fun testDetectTextLoadEachTimeCreatesNewDetectionSession() = runTest {
        val bytes = loadTestResourceBytes(defaultOcrTestCase().imagePath)
        val detectionModel = CountingDetectionModel()
        val localService = createPaddleOcrService(
            detectionModel = detectionModel,
            detectionModelCachePolicy = DetectionModelCachePolicy.LOAD_EACH_TIME,
        )

        try {
            val firstBoxes = localService.detectText(bytes, detectionModel)
            val secondBoxes = localService.detectText(bytes, detectionModel)

            assertThat(firstBoxes).isNotEmpty()
            assertThat(secondBoxes).isNotEmpty()
        } finally {
            localService.close()
        }

        assertThat(detectionModel.modelLoadCount).isEqualTo(2)
    }

    protected fun assertRecognizedTextMatchesBaseline(
        results: List<OcrResult>,
        testCase: OcrTestImageCase = defaultOcrTestCase(),
    ) {
        assertThat(results).isNotEmpty()
        val combinedText = results.joinToString(" ") { it.text }
        val normalized = combinedText.replace(Regex("\\s+"), " ").trim()
        assertThat(normalized).isNotEmpty()
        testCase.expectedTextLines.forEach { expectedText ->
            assertThat(normalized).contains(expectedText)
        }
    }

    protected fun loadOcrTestCases(): List<OcrTestImageCase> {
        val cases = listTestResourceDirectories(OCR_TEST_CASES_PATH).map { casePath ->
            OcrTestImageCase(
                directoryPath = casePath,
                imagePath = "$casePath/image.png",
                expectedTextLines = loadOptionalTextLines("$casePath/text.txt"),
                expectedBoxes = loadOptionalBoxes("$casePath/boxes.txt"),
            )
        }
        assertThat(cases).isNotEmpty()
        return cases
    }

    protected fun defaultOcrTestCase(): OcrTestImageCase = loadOcrTestCases().first()

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

    private fun loadOptionalBoxes(path: String): List<ExpectedTextBox> {
        return runCatching { loadTestResourceBytes(path).decodeToString() }
            .getOrNull()
            ?.let(::parseExpectedBoxes)
            .orEmpty()
    }

    private fun parseExpectedBoxes(text: String): List<ExpectedTextBox> {
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line ->
                val points = line.split(Regex("\\s+"))
                    .map { point ->
                        val coordinates = point.split(",")
                        BoxPoint(coordinates[0].toInt(), coordinates[1].toInt())
                    }
                ExpectedTextBox(points)
            }
            .toList()
    }

    private fun assertDetectedBoxesMatchBaseline(
        actualBoxes: List<DetectedResults>,
        expectedBoxes: List<ExpectedTextBox>,
    ) {
        if (expectedBoxes.isEmpty()) return

        assertThat(actualBoxes.size).isEqualTo(expectedBoxes.size)
        actualBoxes.zip(expectedBoxes).forEach { (actual, expected) ->
            actual.points.zip(expected.points).forEach { (actualPoint, expectedPoint) ->
                assertTrue(abs(actualPoint.x - expectedPoint.x) <= BOX_COORDINATE_TOLERANCE)
                assertTrue(abs(actualPoint.y - expectedPoint.y) <= BOX_COORDINATE_TOLERANCE)
            }
        }
    }
}

data class OcrTestImageCase(
    val directoryPath: String,
    val imagePath: String,
    val expectedTextLines: List<String>,
    val expectedBoxes: List<ExpectedTextBox>,
)

data class ExpectedTextBox(
    val points: List<BoxPoint>,
)

internal class CountingRecognitionModel(
    override val id: String = "counting-base-recognition-model",
    private val delegate: RecognitionModel = BaseRecognitionModel,
) : RecognitionModel by delegate {

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

internal class CountingDetectionModel(
    override val id: String = "counting-base-detection-model",
    private val delegate: DetectionModel = BaseDetectionModel,
) : DetectionModel by delegate {

    var modelLoadCount: Int = 0
        private set

    override suspend fun loadModelBytes(): ByteArray {
        modelLoadCount += 1
        return delegate.loadModelBytes()
    }
}

internal const val OCR_TEST_CASES_PATH = "ocr"
internal val DEFAULT_EXPECTED_TEXT_LINES: List<String> = listOf("Gate of Skye", "Lv")
private const val BOX_COORDINATE_TOLERANCE = 8
