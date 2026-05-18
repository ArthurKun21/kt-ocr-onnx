package com.github.arthurkun.koo.recognition

/**
 * Provides the bytes required to create a PaddleOCR recognition model.
 *
 * Implementations can read bundled resources, local files, network caches, or any other
 * storage location. The OCR engine consumes this interface instead of hard-coding a
 * particular model asset path.
 */
public interface RecognitionModel {

    /** Stable identifier used to distinguish this model from other recognition models. */
    public val id: String

    /** Loads the ONNX recognition model bytes. */
    public suspend fun loadModelBytes(): ByteArray

    /** Loads the PaddleOCR character dictionary bytes. */
    public suspend fun loadDictionaryBytes(): ByteArray
}
