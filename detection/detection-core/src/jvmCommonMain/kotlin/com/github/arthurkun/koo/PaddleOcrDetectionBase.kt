package com.github.arthurkun.koo

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.github.arthurkun.koo.detection.DetectionModel
import com.github.arthurkun.koo.imaging.CvImage
import com.github.micycle1.clipper2.Clipper
import com.github.micycle1.clipper2.core.Path64
import com.github.micycle1.clipper2.core.Paths64
import com.github.micycle1.clipper2.core.Point64
import com.github.micycle1.clipper2.offset.EndType
import com.github.micycle1.clipper2.offset.JoinType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import logcat.LogPriority
import logcat.asLog
import logcat.logcat
import java.nio.FloatBuffer
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.seconds

/**
 * Abstract base class for text detection using a PaddleOCR DB (Differentiable Binarization) ONNX
 * model.
 *
 * Contains all platform-independent logic: ONNX model loading, preprocessing, polygon utilities,
 * and lifecycle management. Platform-specific subclasses implement [dbPostProcess] which uses
 * OpenCV APIs that differ between Android and JVM.
 *
 * ## Pipeline
 *
 * 1. **Preprocessing**: Resize (max side capped at `detLimitSideLen`, round to 32), ImageNet
 *    normalize, HWC -> NCHW
 * 2. **Inference**: ONNX model produces a probability map (1, 1, H, W)
 * 3. **Postprocessing**: DB algorithm — binarize, find contours, score, unclip, scale back
 *
 * ## PaddleOCR Reference
 *
 * - **DetResizeForTest**: https://github.com/PaddlePaddle/PaddleOCR/blob/main/ppocr/data/imaug/operators.py
 * - **DBPostProcess**: https://github.com/PaddlePaddle/PaddleOCR/blob/main/ppocr/postprocess/db_postprocess.py
 * - **PP-OCRv6 config**: configs/det/PP-OCRv6/PP-OCRv6_small_det.yml
 */
@InternalKtOcrONNXApi
public abstract class PaddleOcrDetectionBase(
    @Suppress("UNUSED_PARAMETER") scope: CoroutineScope,
    protected val detectionModel: DetectionModel,
) : AutoCloseable {

    private val dispatcher = Dispatchers.Default.limitedParallelism(1)
    private val mutex = Mutex()

    private val ortEnvRef = AtomicReference<OrtEnvironment?>(null)
    private val ortSessionRef = AtomicReference<OrtSession?>(null)

    private val initFailureRef = AtomicReference<Throwable?>(null)
    private val isInitialized = AtomicBoolean(false)
    private val isClosed = AtomicBoolean(false)

    private suspend fun initializeModel() = withContext(dispatcher) {
        mutex.withLock {
            if (isInitialized.load()) {
                return@withLock
            }

            initFailureRef.load()?.let { failure ->
                throw if (failure is OCRException) {
                    failure
                } else {
                    OCRInitializationException("Detection model initialization failed", cause = failure)
                }
            }

            logcat(TAG) { "Initializing PaddleOCR detection model..." }

            try {
                val env = OrtEnvironment.getEnvironment()
                ortEnvRef.store(env)

                val modelBytes = detectionModel.loadModelBytes()
                logcat(TAG) { "Detection model loaded, size: ${modelBytes.size} bytes" }

                val session = OrtSession.SessionOptions().use { sessionOptions ->
                    sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.EXTENDED_OPT)
                    env.createSession(modelBytes, sessionOptions)
                }
                ortSessionRef.store(session)

                isInitialized.store(true)
                logcat(LogPriority.INFO, TAG) { "PaddleOCR detection initialized successfully" }
            } catch (t: Throwable) {
                if (t is CancellationException) {
                    cleanup()
                    throw t
                }

                initFailureRef.store(t)
                logcat(LogPriority.ERROR, TAG) { "Failed to initialize detection: ${t.asLog()}" }
                cleanup()
                throw OCRInitializationException("Failed to initialize detection model", cause = t)
            }
        }
    }

    private suspend fun ensureInitialized() {
        if (isClosed.load()) {
            throw OCRClosedException("Detection model is already closed")
        }

        initFailureRef.load()?.let { failure ->
            throw if (failure is OCRException) {
                failure
            } else {
                OCRInitializationException("Detection model initialization failed", cause = failure)
            }
        }

        if (!isInitialized.load()) {
            initializeModel()
        }

        if (!isInitialized.load()) {
            throw OCRInitializationException(
                "Detection model initialization failed",
            )
        }
    }

    /**
     * Detects text regions in the given image.
     *
     * @param image Input image in RGB order.
     * @return List of detected text region boxes with scores
     */
    public suspend fun detect(image: CvImage): List<DetectedResults> {
        ensureInitialized()

        return mutex.withLock {
            withContext(dispatcher) {
                try {
                    runDetection(image)
                } catch (t: Throwable) {
                    if (t is CancellationException) {
                        throw t
                    }

                    logcat(LogPriority.ERROR, TAG) { "Error during detection: ${t.asLog()}" }
                    throw if (t is OCRException) {
                        t
                    } else {
                        OCRInferenceException("Error during detection", cause = t)
                    }
                }
            }
        }
    }

    private fun runDetection(inputImage: CvImage): List<DetectedResults> {
        val session = ortSessionRef.load()
            ?: throw OCRModelStateException(
                "Detection session not initialized",
            )
        val env = ortEnvRef.load()
            ?: throw OCRModelStateException(
                "Detection environment not initialized",
            )

        val srcH = inputImage.height
        val srcW = inputImage.width

        // Preprocess
        val (inputTensor, resizeH, resizeW) = preprocessImage(inputImage, env)

        // Run inference
        val output = try {
            session.run(mapOf("x" to inputTensor))
        } finally {
            inputTensor.close()
        }

        val probMap = try {
            val rawOutput = output.get(0).value as? Array<*>
                ?: throw OCRModelOutputException(
                    "Unexpected detection output type",
                )
            val batch = rawOutput.firstOrNull() as? Array<*>
                ?: throw OCRModelOutputException(
                    "Detection output missing batch dimension",
                )
            val channel = batch.firstOrNull() as? Array<*>
                ?: throw OCRModelOutputException(
                    "Detection output missing channel dimension",
                )

            Array(channel.size) { rowIndex ->
                channel[rowIndex] as? FloatArray
                    ?: throw OCRModelOutputException(
                        "Detection output row is not FloatArray at index $rowIndex",
                    )
            }
        } finally {
            output.close()
        }

        // Postprocess with DB algorithm
        return dbPostProcess(probMap, resizeH, resizeW, srcH, srcW)
    }

    /**
     * Preprocesses an image for the detection model.
     *
     * Steps (matching PaddleOCR Python pipeline):
     * 1. DetResizeForTest: resize so max side <= 960, both dims rounded to multiple of 32
     * 2. NormalizeImage: ImageNet normalization (pixel/255 - mean) / std
     * 3. ToCHWImage: HWC → CHW
     * 4. Add batch dim: CHW → NCHW
     *
     * @return Triple of (tensor, resizedHeight, resizedWidth)
     */
    private fun preprocessImage(
        inputImage: CvImage,
        env: OrtEnvironment,
    ): Triple<OnnxTensor, Int, Int> {
        val h = inputImage.height
        val w = inputImage.width

        // DetResizeForTest with limit_type="max" semantics, mirroring the Python inference driver
        // default (tools/infer/utility.py: det_limit_side_len=960, det_limit_type="max").
        val limitSideLen = detectionModel.detLimitSideLen
        val ratio = if (max(h, w) > limitSideLen) {
            limitSideLen.toFloat() / max(h, w).toFloat()
        } else {
            1.0f
        }

        val roundTo = detectionModel.detRoundTo
        val resizeH = max((h * ratio / roundTo).roundToInt() * roundTo, roundTo)
        val resizeW = max((w * ratio / roundTo).roundToInt() * roundTo, roundTo)

        // Resize
        val resizedImage = inputImage.resizeTo(resizeH, resizeW)
        val floatImage = try {
            resizedImage.convertToFloat()
        } finally {
            resizedImage.close()
        }

        // Create NCHW buffer and fill with normalized values
        val bufferSize = 1 * CHANNELS * resizeH * resizeW
        val buffer = FloatBuffer.allocate(bufferSize)

        // Hoisted out of the per-pixel loop: interface getters allocate on each access.
        val mean = detectionModel.detMean
        val std = detectionModel.detStd

        try {
            // Service inputs are normalized to RGB before reaching the shared OCR pipeline.
            // Apply ImageNet mean/std in RGB order.
            for (c in 0 until CHANNELS) {
                for (y in 0 until resizeH) {
                    for (x in 0 until resizeW) {
                        val pixel = floatImage.getPixel(y, x)
                        val normalized = (pixel[c].toFloat() / 255.0f - mean[c]) / std[c]
                        val index = c * resizeH * resizeW + y * resizeW + x
                        buffer.put(index, normalized)
                    }
                }
            }
        } finally {
            floatImage.close()
        }

        buffer.rewind()

        val tensor = OnnxTensor.createTensor(
            env,
            buffer,
            longArrayOf(1, CHANNELS.toLong(), resizeH.toLong(), resizeW.toLong()),
        )

        return Triple(tensor, resizeH, resizeW)
    }

    /**
     * DB (Differentiable Binarization) postprocessing.
     *
     * Platform-specific: uses OpenCV APIs for Mat operations, contour finding, and score computation.
     *
     * @param probMap Probability map from model output, shape [H][W] (row-major)
     * @param mapH Height of the probability map
     * @param mapW Width of the probability map
     * @param srcH Original image height
     * @param srcW Original image width
     * @return List of detected text boxes
     */
    protected abstract fun dbPostProcess(
        probMap: Array<FloatArray>,
        mapH: Int,
        mapW: Int,
        srcH: Int,
        srcW: Int,
    ): List<DetectedResults>

    /**
     * Sorts 4 rectangle corner points into clockwise order:
     * top-left, top-right, bottom-right, bottom-left.
     *
     * Reference: ppocr/postprocess/db_postprocess.py get_mini_boxes
     */
    protected fun getMiniBoxPoints(points: List<DoubleArray>): List<DoubleArray> {
        // Sort by x coordinate
        val sorted = points.sortedBy { it[0] }

        // Left two points (smaller x)
        val left = sorted.subList(0, 2)
        // Right two points (larger x)
        val right = sorted.subList(2, 4)

        // Among left points: smaller y = top-left, larger y = bottom-left
        val (topLeft, bottomLeft) = if (left[0][1] <= left[1][1]) {
            left[0] to left[1]
        } else {
            left[1] to left[0]
        }

        // Among right points: smaller y = top-right, larger y = bottom-right
        val (topRight, bottomRight) = if (right[0][1] <= right[1][1]) {
            right[0] to right[1]
        } else {
            right[1] to right[0]
        }

        return listOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    /**
     * Expands a polygon outward using the Clipper library.
     *
     * The expansion distance is: area * unclip_ratio / perimeter
     *
     * Reference: ppocr/postprocess/db_postprocess.py unclip
     *
     * Uses Clipper2-java with JoinType.Round and EndType.Polygon.
     */
    protected fun unclipPolygon(points: List<DoubleArray>): List<DoubleArray> {
        val area = polygonArea(points)
        val perimeter = polygonPerimeter(points)
        if (perimeter <= 0) return emptyList()

        val distance = area * detectionModel.detUnclipRatio / perimeter

        // Scale to integer coordinates for Clipper2 (it uses long internally)
        val scaleFactor = 1000.0
        val path = Path64()
        for (pt in points) {
            path.add(Point64((pt[0] * scaleFactor).toLong(), (pt[1] * scaleFactor).toLong()))
        }

        val paths = Paths64()
        paths.add(path)

        val expanded = Clipper.inflatePaths(
            paths,
            distance * scaleFactor,
            JoinType.Round,
            EndType.Polygon,
        )

        if (expanded.isEmpty()) return emptyList()

        // Take the first (and typically only) result polygon
        val resultPath = expanded[0]
        return resultPath.map { pt ->
            doubleArrayOf(pt.x / scaleFactor, pt.y / scaleFactor)
        }
    }

    private fun polygonArea(points: List<DoubleArray>): Float {
        val n = points.size
        var area = 0.0
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += points[i][0] * points[j][1]
            area -= points[j][0] * points[i][1]
        }
        return (abs(area) / 2.0).toFloat()
    }

    private fun polygonPerimeter(points: List<DoubleArray>): Float {
        val n = points.size
        var perimeter = 0.0
        for (i in 0 until n) {
            val j = (i + 1) % n
            val dx = points[j][0] - points[i][0]
            val dy = points[j][1] - points[i][1]
            perimeter += sqrt(dx * dx + dy * dy)
        }
        return perimeter.toFloat()
    }

    private fun cleanup() {
        try {
            ortSessionRef.load()?.close()
            ortSessionRef.store(null)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, TAG) { "Failed to close session: ${e.asLog()}" }
        }

        try {
            ortEnvRef.load()?.close()
            ortEnvRef.store(null)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, TAG) { "Failed to close environment: ${e.asLog()}" }
        }

        isInitialized.store(false)
    }

    public suspend fun closeSuspending() {
        if (!isClosed.compareAndSet(false, true)) {
            return
        }

        try {
            mutex.withLock {
                withTimeout(15.seconds) {
                    cleanup()
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, TAG) { "Error closing detection: ${e.asLog()}" }
        }
    }

    public override fun close() {
        runBlocking {
            closeSuspending()
        }
    }
}

private const val TAG = "PaddleOcrDetection"
