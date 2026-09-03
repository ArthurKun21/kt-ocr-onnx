package com.github.arthurkun.koo.detection

public enum class DetectionModelCachePolicy {
    /** Keep initialized detection sessions in memory for reuse. */
    KEEP_IN_MEMORY,

    /** Create and close a detection session for each request. */
    LOAD_EACH_TIME,
}
