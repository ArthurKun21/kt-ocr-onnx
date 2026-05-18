package com.github.arthurkun.koo

import android.graphics.Bitmap
import android.net.Uri
import org.opencv.core.Mat

public interface AndroidDetectionApi : DetectionApi {

    public suspend fun detectText(bitmap: Bitmap): List<DetectedResults>

    public suspend fun detectText(uri: Uri): List<DetectedResults>

    public suspend fun detectText(mat: Mat): List<DetectedResults>
}
