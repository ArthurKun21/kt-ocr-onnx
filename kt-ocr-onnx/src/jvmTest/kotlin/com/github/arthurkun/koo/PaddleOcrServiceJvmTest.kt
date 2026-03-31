package com.github.arthurkun.koo

/**
 * JVM tests for PaddleOcrService.
 *
 * Tests the PaddleOCR v5 recognition model using shared test assets.
 * Extends [PaddleOcrServiceTestBase] for shared test logic.
 * Test assets are loaded from the JVM classpath resources.
 */
class PaddleOcrServiceJvmTest : PaddleOcrServiceTestBase() {

    override fun loadTestResourceBytes(path: String): ByteArray {
        val inputStream = requireNotNull(
            Thread.currentThread().contextClassLoader?.getResourceAsStream(path),
        ) {
            "Test resource not found: $path"
        }

        return inputStream.readBytes()
    }
}
