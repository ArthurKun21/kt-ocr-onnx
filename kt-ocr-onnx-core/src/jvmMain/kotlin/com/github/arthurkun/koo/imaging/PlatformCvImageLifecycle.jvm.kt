package com.github.arthurkun.koo.imaging

import com.github.arthurkun.koo.InternalKtOcrONNXApi
import org.bytedeco.opencv.opencv_core.Mat

@InternalKtOcrONNXApi
public suspend fun <T> withRgbCvImageFromMat(
    mat: Mat,
    tag: String = "ocr_input",
    block: suspend (CvImage) -> T,
): T {
    val rgbImage = NativeMat(mat, tag).toRgbCvImage()
    return try {
        block(rgbImage)
    } finally {
        rgbImage.close()
    }
}
