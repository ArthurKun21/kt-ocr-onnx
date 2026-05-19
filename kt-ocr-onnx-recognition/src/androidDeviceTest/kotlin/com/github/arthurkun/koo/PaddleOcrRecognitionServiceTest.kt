package com.github.arthurkun.koo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import com.github.arthurkun.koo.recognition.RecognitionModel
import com.github.arthurkun.koo.recognition.RecognitionModelCachePolicy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileNotFoundException

/**
 * Instrumented tests for PaddleOcrRecognitionService on Android.
 *
 * Tests the recognition-only wrapper using shared test assets.
 * Extends [PaddleOcrRecognitionServiceTestBase] for shared test logic and adds
 * Android-specific tests for Bitmap recognition.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(InternalKtOcrONNXApi::class)
class PaddleOcrRecognitionServiceTest : PaddleOcrRecognitionServiceTestBase() {
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
        return loadAndroidTestAssetBytes(path)
    }

    override fun createPaddleOcrRecognitionService(
        recognitionModel: RecognitionModel,
        recognitionModelCachePolicy: RecognitionModelCachePolicy,
    ): RecognitionApi {
        return PaddleOcrRecognitionService(
            platformContext = context,
            recognitionModel = recognitionModel,
            recognitionModelCachePolicy = recognitionModelCachePolicy,
        )
    }

    @Before
    override fun setUp() {
        paddleOcrRecognitionService = createPaddleOcrRecognitionService()
    }

    @After
    override fun tearDown() {
        super.tearDown()
    }

    @Test
    fun testRecognizeTextFromTestImageBitmapUsesDefaultRecognitionModel() = runTest {
        val bitmap = loadImageBitmap(TEST_IMAGE_PATH)

        try {
            val result = (paddleOcrRecognitionService as AndroidRecognitionApi).recognizeText(bitmap)
            assertRecognizedTextMatchesBaseline(result)
            Log.i(TAG, "Recognized text: '${result.text}'")
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun testRecognizeTextBitmapUsesExplicitRecognitionSession() = runTest {
        val bitmap = loadImageBitmap(TEST_IMAGE_PATH)
        val recognitionModel = CountingRecognitionModel()
        val bitmapService = PaddleOcrRecognitionService(
            platformContext = context,
            recognitionModel = recognitionModel,
        )

        try {
            val result = (bitmapService as AndroidRecognitionApi).recognizeText(bitmap, recognitionModel)
            assertRecognizedTextMatchesBaseline(result)
            assertThat(recognitionModel.modelLoadCount).isEqualTo(1)
            assertThat(recognitionModel.dictionaryLoadCount).isEqualTo(1)
            Log.i(TAG, "Recognized text: '${result.text}'")
        } finally {
            bitmapService.close()
            bitmap.recycle()
        }
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

private const val TAG = "PaddleOcrRecognitionServiceTest"
private const val COMPOSE_TEST_ASSET_PREFIX =
    "composeResources/com.github.arthurkun.koo.recognition.base.resources/files/"

private fun loadAndroidTestAssetBytes(path: String): ByteArray {
    val assets = InstrumentationRegistry.getInstrumentation().context.assets

    for (candidatePath in androidTestAssetCandidates(path)) {
        try {
            return assets.open(candidatePath).use { it.readBytes() }
        } catch (_: FileNotFoundException) {
            // Try the next candidate path.
        }
    }

    throw FileNotFoundException("Unable to open Android test asset for path '$path'.")
}

private fun androidTestAssetCandidates(path: String): List<String> {
    return listOf(
        "$COMPOSE_TEST_ASSET_PREFIX$path",
        path,
    ).distinct()
}
