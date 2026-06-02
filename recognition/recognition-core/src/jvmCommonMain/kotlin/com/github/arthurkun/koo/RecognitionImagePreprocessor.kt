package com.github.arthurkun.koo

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import com.github.arthurkun.koo.imaging.CvImage

/**
 * Preprocesses an image for the PaddleOCR v5 recognition model.
 *
 * Matches `tools/infer/predict_rec.py::TextRecognizer.resize_norm_img` for PP-OCRv5 mobile:
 * resize to height 48, preserve aspect ratio up to width 320, normalize with `(x / 255 - 0.5) / 0.5`,
 * right-pad with zeroes in normalized float space, and write NCHW layout.
 */
@OptIn(InternalKtOcrONNXApi::class)
internal expect fun preprocessRecognitionImage(inputImage: CvImage, env: OrtEnvironment): OnnxTensor
