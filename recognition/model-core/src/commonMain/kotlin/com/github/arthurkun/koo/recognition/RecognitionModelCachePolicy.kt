package com.github.arthurkun.koo.recognition

/** Controls whether recognition model assets are retained after they are loaded. */
public enum class RecognitionModelCachePolicy {
    /** Keep the ONNX model bytes and parsed dictionary in memory for reuse. */
    KEEP_IN_MEMORY,

    /** Ask the [RecognitionModel] to load its bytes whenever the model data is requested. */
    LOAD_EACH_TIME,
}
