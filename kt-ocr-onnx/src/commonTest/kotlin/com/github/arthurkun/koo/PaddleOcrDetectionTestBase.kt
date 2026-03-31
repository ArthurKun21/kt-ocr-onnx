package com.github.arthurkun.koo

import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.isGreaterThan
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isNotEmpty
import com.github.arthurkun.koo.imaging.CvImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Abstract base class for PaddleOcrDetection tests.
 *
 * Provides shared test logic for both JVM and Android device tests.
 * Each platform implements [loadTestResourceBytes] to load test assets
 * from the appropriate location.
 */
abstract class PaddleOcrDetectionTestBase {

    abstract fun loadTestResourceBytes(path: String): ByteArray

    private lateinit var detection: PaddleOcrDetection
    protected lateinit var testScope: CoroutineScope

    @BeforeTest
    open fun setUp() {
        testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        detection = PaddleOcrDetection(testScope)
    }

    @AfterTest
    open fun tearDown() {
        detection.close()
    }

    protected suspend fun loadTestImage(path: String): CvImage {
        val bytes = loadTestResourceBytes(path)
        return CvImage.fromByteArray(bytes, isColor = true, tag = "det-test")
    }

    @Test
    fun testDetectReturnsBoxes() = runTest {
        val image = loadTestImage("ocr/noble-phantasm-en.png")
        val boxes = detection.detect(image)
        assertThat(boxes).isNotEmpty()
        image.close()
    }

    @Test
    fun testDetectedBoxesHaveValidCoordinates() = runTest {
        val image = loadTestImage("ocr/noble-phantasm-en.png")
        val w = image.width
        val h = image.height
        val boxes = detection.detect(image)

        assertThat(boxes).isNotEmpty()
        assertThat(boxes).each { box ->
            box.transform { it.points }.each { point ->
                point.transform { it.x }.isGreaterThanOrEqualTo(0)
                point.transform { it.x }.isLessThanOrEqualTo(w)
                point.transform { it.y }.isGreaterThanOrEqualTo(0)
                point.transform { it.y }.isLessThanOrEqualTo(h)
            }
            box.transform { it.score }.isGreaterThan(DET_BOX_THRESH)
        }

        image.close()
    }
}
