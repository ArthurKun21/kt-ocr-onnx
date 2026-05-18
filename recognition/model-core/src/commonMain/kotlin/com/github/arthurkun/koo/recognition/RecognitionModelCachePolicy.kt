package com.github.arthurkun.koo.recognition

/** Controls whether recognition sessions are reused between OCR requests. */
public enum class RecognitionModelCachePolicy {
    /** Keep initialized recognition sessions in memory for reuse. */
    KEEP_IN_MEMORY,

    /** Create and close a recognition session for each request. */
    LOAD_EACH_TIME,
}
