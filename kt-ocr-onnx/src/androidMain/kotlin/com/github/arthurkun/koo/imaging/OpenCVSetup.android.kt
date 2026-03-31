package com.github.arthurkun.koo.imaging

import org.opencv.android.OpenCVLoader

/**
 * Initializes OpenCV library.
 */
internal actual fun initOpenCV() {
    OpenCVLoader.initLocal()
}
