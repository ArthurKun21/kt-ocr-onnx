package com.github.arthurkun.koo.imaging

import com.github.arthurkun.koo.InternalKtOcrONNXApi

@InternalKtOcrONNXApi
public suspend fun <T> withRgbCvImageFromByteArray(
    byteArray: ByteArray,
    tag: String = "ocr_input",
    block: suspend (CvImage) -> T,
): T {
    val image = CvImage.fromByteArray(byteArray, isColor = true, tag = tag)
    return try {
        withRgbCvImage(image, block)
    } finally {
        image.close()
    }
}

@InternalKtOcrONNXApi
public suspend fun <T> withRgbCvImage(
    image: CvImage,
    block: suspend (CvImage) -> T,
): T {
    val rgbImage = image.toRgbCvImage()
    return try {
        block(rgbImage)
    } finally {
        rgbImage.close()
    }
}
