package com.github.arthurkun.koo

import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

public interface DetectionApi : AutoCloseable {

    public suspend fun detectText(byteArray: ByteArray): List<DetectedResults>

    public suspend fun detectText(source: Source): List<DetectedResults> =
        detectText(source.readByteArray())

    public suspend fun detectText(path: String): List<DetectedResults> =
        detectText(Path(path))

    public suspend fun detectText(path: Path): List<DetectedResults> {
        val bytes = SystemFileSystem.source(path).buffered().use { it.readByteArray() }
        return detectText(bytes)
    }
}
