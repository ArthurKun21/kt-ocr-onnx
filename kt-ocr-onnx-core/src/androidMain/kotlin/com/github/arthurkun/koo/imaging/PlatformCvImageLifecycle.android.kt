package com.github.arthurkun.koo.imaging

import android.graphics.Bitmap
import com.github.arthurkun.koo.InternalKtOcrONNXApi
import org.opencv.core.Mat

@InternalKtOcrONNXApi
public suspend fun <T> withRgbCvImageFromBitmap(
    bitmap: Bitmap,
    tag: String = "ocr_input",
    block: suspend (CvImage) -> T,
): T {
    val image = cvImageFromBitmap(bitmap, tag)
    return try {
        withRgbCvImage(image, block)
    } finally {
        image.close()
    }
}

@InternalKtOcrONNXApi
public suspend fun <T> withBgrCvImageFromBitmap(
    bitmap: Bitmap,
    tag: String = "ocr_input",
    block: suspend (CvImage) -> T,
): T {
    val image = cvImageFromBitmap(bitmap, tag)
    return try {
        withBgrCvImage(image, block)
    } finally {
        image.close()
    }
}

@InternalKtOcrONNXApi
public suspend fun <T> withCvImageFromBitmap(
    bitmap: Bitmap,
    tag: String = "ocr_input",
    block: suspend (CvImage) -> T,
): T {
    val image = cvImageFromBitmap(bitmap, tag)
    return try {
        block(image)
    } finally {
        image.close()
    }
}

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

@InternalKtOcrONNXApi
public suspend fun <T> withCvImageFromMat(
    mat: Mat,
    tag: String = "ocr_input",
    block: suspend (CvImage) -> T,
): T {
    val image = NativeMat(mat, tag)
    return block(image)
}
