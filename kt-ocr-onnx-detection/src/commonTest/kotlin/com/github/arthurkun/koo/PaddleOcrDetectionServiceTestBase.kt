package com.github.arthurkun.koo

import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isNotEmpty
import com.github.arthurkun.koo.imaging.CvImage
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Abstract base class for PaddleOcrDetectionService tests.
 *
 * Provides shared test logic for both JVM and Android device tests.
 * Each platform implements [loadTestResourceBytes] to load test assets
 * from the appropriate location.
 */
@OptIn(InternalKtOcrONNXApi::class)
abstract class PaddleOcrDetectionServiceTestBase {

    abstract fun loadTestResourceBytes(path: String): ByteArray

    abstract fun listTestResourceDirectories(path: String): List<String>

    protected abstract fun createPaddleOcrDetectionService(): DetectionApi

    private lateinit var detectionService: DetectionApi

    @BeforeTest
    open fun setUp() {
        initOpenCV()
        detectionService = createPaddleOcrDetectionService()
    }

    @AfterTest
    open fun tearDown() {
        detectionService.close()
    }

    private suspend fun loadTestImage(path: String): CvImage {
        val bytes = loadTestResourceBytes(path)
        val image = CvImage.fromByteArray(bytes, isColor = true, tag = "det-test")
        return try {
            image.toRgbCvImage()
        } finally {
            image.close()
        }
    }

    @Test
    fun testDetectReturnsBoxes() = runTest {
        for (testCase in loadDetectionTestCases()) {
            val boxes = detectionService.detectText(loadTestResourceBytes(testCase.imagePath))
            assertThat(boxes).isNotEmpty()
        }
    }

    @Test
    fun testDetectedBoxesHaveValidCoordinates() = runTest {
        for (testCase in loadDetectionTestCases()) {
            val image = loadTestImage(testCase.imagePath)
            try {
                val w = image.width
                val h = image.height
                val boxes = detectionService.detectText(loadTestResourceBytes(testCase.imagePath))

                assertThat(boxes).isNotEmpty()
                assertThat(boxes).each { box ->
                    box.transform { it.points }.each { point ->
                        point.transform { it.x }.isGreaterThanOrEqualTo(0)
                        point.transform { it.x }.isLessThanOrEqualTo(w)
                        point.transform { it.y }.isGreaterThanOrEqualTo(0)
                        point.transform { it.y }.isLessThanOrEqualTo(h)
                    }
                    box.transform { it.score }.isGreaterThan(DETECTION_BOX_THRESHOLD)
                }
                assertDetectedBoxesMatchBaseline(boxes, testCase.expectedBoxes)
            } finally {
                image.close()
            }
        }
    }

    protected fun loadDetectionTestCases(): List<DetectionTestImageCase> {
        val cases = listTestResourceDirectories(OCR_TEST_CASES_PATH).map { casePath ->
            DetectionTestImageCase(
                directoryPath = casePath,
                imagePath = "$casePath/image.png",
                expectedBoxes = loadOptionalBoxes("$casePath/boxes.txt"),
            )
        }
        assertThat(cases).isNotEmpty()
        return cases
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

data class DetectionTestImageCase(
    val directoryPath: String,
    val imagePath: String,
    val expectedBoxes: List<ExpectedTextBox>,
)

data class ExpectedTextBox(
    val points: List<BoxPoint>,
)

internal const val OCR_TEST_CASES_PATH = "ocr"
private const val DETECTION_BOX_THRESHOLD = 0.6f
private const val BOX_COORDINATE_TOLERANCE = 8
