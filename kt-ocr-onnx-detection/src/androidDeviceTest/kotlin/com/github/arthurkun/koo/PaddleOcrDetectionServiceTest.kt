package com.github.arthurkun.koo

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.runner.RunWith
import java.io.FileNotFoundException

@RunWith(AndroidJUnit4::class)
@OptIn(InternalKtOcrONNXApi::class)
class PaddleOcrDetectionServiceTest : PaddleOcrDetectionServiceTestBase() {
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

    override fun listTestResourceDirectories(path: String): List<String> {
        return listAndroidTestAssetDirectories(path)
    }

    override fun createPaddleOcrDetectionService(): DetectionApi {
        return PaddleOcrDetectionService(platformContext = context)
    }

    @Before
    override fun setUp() {
        super.setUp()
    }

    @After
    override fun tearDown() {
        super.tearDown()
    }
}

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

private fun listAndroidTestAssetDirectories(path: String): List<String> {
    val assets = InstrumentationRegistry.getInstrumentation().context.assets
    return androidTestAssetCandidates(path)
        .asSequence()
        .flatMap { root ->
            assets.list(root).orEmpty().asSequence().mapNotNull { child ->
                val childPath = "$root/$child"
                val originalPath = "$path/$child"
                if (assets.list(childPath).orEmpty().isNotEmpty()) originalPath else null
            }
        }
        .distinct()
        .sorted()
        .toList()
}
