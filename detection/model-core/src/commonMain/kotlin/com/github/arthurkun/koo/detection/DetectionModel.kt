package com.github.arthurkun.koo.detection

public interface DetectionModel {
    /** Stable identifier used to distinguish this model from other detection models. */
    public val id: String

    /** Loads the ONNX detection model bytes. */
    public suspend fun loadModelBytes(): ByteArray
}
