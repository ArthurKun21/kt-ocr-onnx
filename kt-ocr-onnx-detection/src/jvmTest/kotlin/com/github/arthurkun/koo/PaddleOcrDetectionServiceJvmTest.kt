package com.github.arthurkun.koo

/**
 * JVM tests for PaddleOcrDetectionService.
 *
 * Tests the detection-only wrapper using shared test assets.
 * Extends [PaddleOcrDetectionServiceTestBase] for shared test logic.
 * Test assets are loaded from the JVM classpath resources.
 */
class PaddleOcrDetectionServiceJvmTest : PaddleOcrDetectionServiceTestBase() {

    override fun createPaddleOcrDetectionService(): DetectionApi {
        return PaddleOcrDetectionService()
    }

    override fun loadTestResourceBytes(path: String): ByteArray {
        val inputStream = requireNotNull(
            Thread.currentThread().contextClassLoader?.getResourceAsStream(path),
        ) {
            "Test resource not found: $path"
        }

        return inputStream.readBytes()
    }
}
