package com.github.arthurkun.koo.imaging

import org.opencv.android.OpenCVLoader

/**
 * Initializes OpenCV library.
 */
public actual fun initializeOpenCvRuntime() {
    OpenCVLoader.initLocal()
}
