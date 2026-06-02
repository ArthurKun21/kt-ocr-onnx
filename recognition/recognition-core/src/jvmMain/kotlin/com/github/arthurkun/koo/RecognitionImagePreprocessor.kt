package com.github.arthurkun.koo

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import com.github.arthurkun.koo.imaging.CvImage
import com.github.arthurkun.koo.imaging.NativeMat
import org.bytedeco.javacpp.indexer.FloatIndexer
import org.bytedeco.opencv.global.opencv_core.CV_32FC3
import org.bytedeco.opencv.global.opencv_imgproc.resize
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Size
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
        resize(nativeImage.mat, resizedImage, Size(resizedW, TARGET_HEIGHT))
        resizedImage.convertTo(floatImage, CV_32FC3)

        val buffer = FloatBuffer.allocate(1 * CHANNELS * TARGET_HEIGHT * TARGET_WIDTH)
        floatImage.createIndexer<FloatIndexer>().use { indexer ->
            for (c in 0 until CHANNELS) {
                for (y in 0 until TARGET_HEIGHT) {
                    for (x in 0 until resizedW) {
                        val pixel = indexer.get(y.toLong(), x.toLong(), c.toLong())
                        val normalized = (pixel / 255.0f - 0.5f) / 0.5f
                        buffer.put(c * TARGET_HEIGHT * TARGET_WIDTH + y * TARGET_WIDTH + x, normalized)
                    }
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
        resizedImage.close()
        floatImage.close()
    }
}

private fun recognitionResizeWidth(width: Int, height: Int): Int {
    val ratio = width.toDouble() / height.toDouble()
    return minOf(ceil(TARGET_HEIGHT * ratio).toInt(), TARGET_WIDTH)
}
