package com.github.arthurkun.koo

import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.isGreaterThan
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isNotEmpty
import com.github.arthurkun.koo.imaging.CvImage
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

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
        val boxes = detectionService.detectText(loadTestResourceBytes(TEST_IMAGE_PATH))
        assertThat(boxes).isNotEmpty()
    }

    @Test
    fun testDetectedBoxesHaveValidCoordinates() = runTest {
        val image = loadTestImage(TEST_IMAGE_PATH)
        val w = image.width
        val h = image.height
        val boxes = detectionService.detectText(loadTestResourceBytes(TEST_IMAGE_PATH))

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

        image.close()
    }
}

private const val TEST_IMAGE_PATH = "ocr/noble-phantasm-en.png"
private const val DETECTION_BOX_THRESHOLD = 0.6f
