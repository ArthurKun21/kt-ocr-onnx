package com.github.arthurkun.koo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import com.github.arthurkun.koo.imaging.initOpenCV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for PaddleOcrService on Android.
 *
 * Tests the PaddleOCR v5 recognition model using shared test assets.
 * Extends [PaddleOcrServiceTestBase] for shared test logic and adds
 * Android-specific tests (e.g., Bitmap detection).
 */
@RunWith(AndroidJUnit4::class)
class PaddleOcrServiceTest : PaddleOcrServiceTestBase() {
    companion object {
        private lateinit var context: Context

        @JvmStatic
        @BeforeClass
        fun setUpClass() {
            initOpenCV()
            context = InstrumentationRegistry.getInstrumentation().targetContext
        }
    }

    override fun loadTestResourceBytes(path: String): ByteArray {
        return Thread.currentThread().contextClassLoader!!.getResourceAsStream(path)!!.readBytes()
    }

    @Before
    override fun setUp() {
        testScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        paddleOcrService = PaddleOcrService(testScope, platformContext = context)
    }

    @After
    override fun tearDown() {
        super.tearDown()
    }

    @Test
    fun testDetectAndRecognizeTextFromTestImageBitmap() = runTest {
        val bitmap = loadImageBitmap("ocr/noble-phantasm-en.png")
        val results = (paddleOcrService as AndroidOcrApi).detectAndRecognizeText(bitmap)
        assertThat(results).isNotEmpty()
        val combinedText = results.joinToString(" ") { it.text }
        val normalized = combinedText.replace(Regex("\\s+"), " ").trim()
        assertThat(normalized).isNotEmpty()
        assertThat(normalized).contains("Gate of Skye")
        assertThat(normalized).contains("Lv")
        Log.i(TAG, "Recognized text: '$combinedText'")
        bitmap.recycle()
    }

    private fun loadImageBitmap(path: String): Bitmap {
        val bytes = loadTestResourceBytes(path)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertThat(bitmap).isNotNull()
        assertThat(bitmap.width).isGreaterThan(0)
        assertThat(bitmap.height).isGreaterThan(0)
        return bitmap
    }
}

private const val TAG = "PaddleOcrServiceTest"
