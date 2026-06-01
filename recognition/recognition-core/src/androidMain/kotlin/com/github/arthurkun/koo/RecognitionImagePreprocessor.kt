package com.github.arthurkun.koo

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import com.github.arthurkun.koo.imaging.CvImage
import com.github.arthurkun.koo.imaging.NativeMat
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.FloatBuffer
import kotlin.math.ceil

@OptIn(InternalKtOcrONNXApi::class)
internal actual fun preprocessRecognitionImage(inputImage: CvImage, env: OrtEnvironment): OnnxTensor {
    if (inputImage.isEmpty()) {
        throw OCRImageProcessingException("Input image is empty")
    }

    val resizedW = recognitionResizeWidth(inputImage.width, inputImage.height)
    val nativeImage = inputImage as? NativeMat
        ?: throw OCRImageProcessingException("Recognition requires an OpenCV-backed image")

    val resizedImage = Mat()
    val floatImage = Mat()
    return try {
        Imgproc.resize(nativeImage.mat, resizedImage, Size(resizedW.toDouble(), TARGET_HEIGHT.toDouble()))
        resizedImage.convertTo(floatImage, CvType.CV_32FC3)

        val buffer = FloatBuffer.allocate(1 * CHANNELS * TARGET_HEIGHT * TARGET_WIDTH)
        val pixel = FloatArray(CHANNELS)
        for (c in 0 until CHANNELS) {
            for (y in 0 until TARGET_HEIGHT) {
                for (x in 0 until resizedW) {
                    floatImage.get(y, x, pixel)
                    val normalized = (pixel[c] / 255.0f - 0.5f) / 0.5f
                    buffer.put(c * TARGET_HEIGHT * TARGET_WIDTH + y * TARGET_WIDTH + x, normalized)
                }
            }
        }
        buffer.rewind()

        OnnxTensor.createTensor(
            env,
            buffer,
            longArrayOf(1, CHANNELS.toLong(), TARGET_HEIGHT.toLong(), TARGET_WIDTH.toLong()),
        )
    } finally {
        resizedImage.release()
        floatImage.release()
    }
}

private fun recognitionResizeWidth(width: Int, height: Int): Int {
    val ratio = width.toDouble() / height.toDouble()
    return minOf(ceil(TARGET_HEIGHT * ratio).toInt(), TARGET_WIDTH)
}
