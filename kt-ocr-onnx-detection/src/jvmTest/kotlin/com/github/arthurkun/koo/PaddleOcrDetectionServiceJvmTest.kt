package com.github.arthurkun.koo

import java.nio.file.Files
import java.nio.file.Paths

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

    override fun listTestResourceDirectories(path: String): List<String> {
        val resource = requireNotNull(Thread.currentThread().contextClassLoader?.getResource(path)) {
            "Test resource directory not found: $path"
        }
        val directory = Paths.get(resource.toURI())
        return Files.list(directory).use { paths ->
            paths
                .filter { Files.isDirectory(it) }
                .map { "$path/${it.fileName}" }
                .sorted()
                .toList()
        }
    }
}
