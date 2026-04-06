package com.github.arthurkun.koo

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.github.arthurkun.koo.imaging.CvImage
import com.github.arthurkun.koo.resources.Res
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
import kotlin.math.ceil
import kotlin.time.Duration.Companion.seconds

/**
 * OCR Recognition using PaddleOCR v5 ONNX model for text recognition.
 *
 * This class loads a pre-trained PaddleOCR recognition model and uses ONNX Runtime
 * for inference. It recognizes text from cropped image regions.
 *
 * ## PaddleOCR Reference
 *
 * The implementation is based on the PaddleOCR project:
 * - **Main Repository**: https://github.com/PaddlePaddle/PaddleOCR
 * - **PP-OCRv5 Documentation**: https://github.com/PaddlePaddle/PaddleOCR/blob/main/doc/doc_en/PP-OCRv5_en.md
 *
 * ## Preprocessing Reference
 *
 * The image preprocessing logic is derived from the PaddleOCR Python implementation:
 * - **resize_norm_img function**: https://github.com/PaddlePaddle/PaddleOCR/blob/main/ppocr/data/imaug/rec_img_aug.py
 * - **RecResizeImg operator**: https://github.com/PaddlePaddle/PaddleOCR/blob/main/ppocr/data/imaug/operators.py
 *
 * The preprocessing steps are:
 * 1. Resize image to height of 48 pixels while maintaining aspect ratio
 * 2. If resized width exceeds 320, cap it at 320
 * 3. Normalize pixel values: `(x / 255 - 0.5) / 0.5` to get [-1, 1] range
 * 4. Pad image to width of 320 with zeros (right padding)
 * 5. Convert from HWC to NCHW format: (1, 3, 48, 320)
 *
 * ## CTC Decoding Reference
 *
 * The CTC (Connectionist Temporal Classification) decoding logic is based on:
 * - **CTCLabelDecode**: https://github.com/PaddlePaddle/PaddleOCR/blob/main/ppocr/postprocess/rec_postprocess.py
 *
 * CTC greedy decoding steps:
 * 1. Get argmax for each timestep in the output sequence
 * 2. Skip blank tokens (index 0)
 * 3. Skip consecutive duplicate character indices
 * 4. Map remaining indices to characters using the dictionary
 *
 * ## Dictionary Reference
 *
 * The character dictionary format follows PaddleOCR conventions:
 * - **ppocr_keys_v1.txt**: https://github.com/PaddlePaddle/PaddleOCR/blob/main/ppocr/utils/ppocr_keys_v1.txt
 * - **PaddleOCR v5 dictionary**: https://github.com/PaddlePaddle/PaddleOCR/blob/a38c087bcb2579f9ccc2068aea02ec893b1c2311/ppocr/utils/dict/ppocrv5_dict.txt
 * - Index 0 is reserved for the CTC blank token
 * - Characters from the dictionary file start at index 1
 *
 * ## ONNX Model Export
 *
 * For information on exporting PaddleOCR models to ONNX format:
 * - **Paddle2ONNX**: https://github.com/PaddlePaddle/Paddle2ONNX
 * - **Export Guide**: https://github.com/PaddlePaddle/PaddleOCR/blob/main/deploy/paddle2onnx/readme.md
 *
 * ## Model Input/Output Specification
 *
 * - **Input name**: "x"
 * - **Input shape**: (1, 3, 48, 320) - (batch, channels, height, width)
 * - **Input range**: [-1, 1] normalized float values
 * - **Output shape**: (1, sequence_length, num_classes)
 * - **Output**: Log probabilities for each character class at each timestep
 */
internal class PaddleOcrRecognition(
    scope: CoroutineScope,
    private val modelPath: String = MODEL_PATH,
    private val dictPath: String = DICT_PATH,
) : AutoCloseable {

    private val dispatcher = Dispatchers.Default.limitedParallelism(1)
    private val mutex = Mutex()

    private val ortEnvRef = AtomicReference<OrtEnvironment?>(null)
    private val ortSessionRef = AtomicReference<OrtSession?>(null)
    private val dictionaryRef = AtomicReference<Map<Int, String>>(emptyMap())

    private val isInitialized = AtomicBoolean(false)
    private val isClosed = AtomicBoolean(false)

    private val initJob: Job

    init {
        initJob = scope.launch {
            initializeModel()
        }
    }

    private suspend fun initializeModel() = withContext(dispatcher) {
        mutex.withLock {
            logcat(TAG) { "Initializing PaddleOCR model..." }

            try {
                // Load dictionary first
                val dictionary = loadDictionary()
                if (dictionary.isEmpty()) {
                    logcat(LogPriority.ERROR, TAG) { "Failed to load dictionary" }
                    return@withLock
                }
                dictionaryRef.store(dictionary)
                logcat(TAG) { "Dictionary loaded with ${dictionary.size} characters" }

                // Create ONNX Runtime environment
                val env = OrtEnvironment.getEnvironment()
                ortEnvRef.store(env)

                // Load model from assets
                val modelBytes = Res.readBytes(modelPath)
                logcat(TAG) { "Model loaded, size: ${modelBytes.size} bytes" }

                // Create session
                val sessionOptions = OrtSession.SessionOptions().apply {
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.EXTENDED_OPT)
                }
                val session = env.createSession(modelBytes, sessionOptions)
                ortSessionRef.store(session)

                isInitialized.store(true)
                logcat(LogPriority.INFO, TAG) { "PaddleOCR initialized successfully" }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, TAG) { "Failed to initialize PaddleOCR: ${e.asLog()}" }
                cleanup()
            }
        }
    }

    /**
     * Loads the character dictionary from assets.
     *
     * The dictionary format follows PaddleOCR conventions:
     * - **PaddleOCR v5 dictionary**: https://github.com/PaddlePaddle/PaddleOCR/blob/a38c087bcb2579f9ccc2068aea02ec893b1c2311/ppocr/utils/dict/ppocrv5_dict.txt
     * - **Reference**: https://github.com/PaddlePaddle/PaddleOCR/blob/main/ppocr/utils/ppocr_keys_v1.txt
     * - **BaseRecLabelDecode**: https://github.com/PaddlePaddle/PaddleOCR/blob/main/ppocr/postprocess/rec_postprocess.py
     *
     * Index 0 is reserved for blank/CTC token.
     * Characters from the file start at index 1.
     * Space character is appended at the end (use_space_char=True convention).
     *
     * @return Map of index to character, empty on failure
     */
    private suspend fun loadDictionary(): Map<Int, String> {
        return try {
            val charDict = mutableMapOf<Int, String>()
            // Index 0 is blank token for CTC decoding
            charDict[0] = "blank"

            var index = 1
            Res.readBytes(dictPath).inputStream().buffered().reader().useLines { lines ->
                lines.forEach { line ->
                    // Strip newlines and carriage returns like Python does
                    val char = line.trimEnd('\n', '\r')
                    if (char.isNotEmpty()) {
                        charDict[index] = char
                        index++
                    }
                }
            }

            // Append space character at the end (use_space_char=True in PaddleOCR)
            // Reference: BaseRecLabelDecode.__init__ in rec_postprocess.py
            charDict[index] = " "

            charDict
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, TAG) { "Failed to load dictionary: ${e.asLog()}" }
            emptyMap()
        }
    }

    /**
     * Ensures the model is initialized before use.
     *
     * @throws OCRException if initialization failed
     */
    private suspend fun ensureInitialized() {
        if (isClosed.load()) {
            throw OCRException(
                OCRReason.LoadingError,
                cause = IllegalStateException("Recognition model is already closed"),
            )
        }
        if (!initJob.isCompleted) {
            logcat(TAG) { "Waiting for PaddleOCR initialization..." }
            initJob.join()
            logcat(TAG) { "PaddleOCR initialization job finished." }
        }
        if (!isInitialized.load()) {
            throw OCRException(
                OCRReason.InitializationError,
                cause = IllegalStateException("PaddleOCR initialization failed"),
            )
        }
    }

    /**
     * Runs the OCR model on an image.
     *
     * @param image Input image (cropped text region)
     * @return [RecognitionResult] with recognized text and confidence score, or empty result on error
     */
    suspend fun detectText(image: CvImage): RecognitionResult {
        ensureInitialized()

        return mutex.withLock {
            withContext(dispatcher) {
                try {
                    runInference(image)
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, TAG) { "Error during OCR: ${e.asLog()}" }
                    throw if (e is OCRException) {
                        e
                    } else {
                        OCRException(OCRReason.LoadingError, cause = e)
                    }
                }
            }
        }
    }

    /**
     * Runs inference on the preprocessed image.
     *
     * @param inputImage Input CvImage (cropped text region)
     * @return [RecognitionResult] with recognized text and confidence score
     */
    private fun runInference(inputImage: CvImage): RecognitionResult {
        val session = ortSessionRef.load()
            ?: throw OCRException(OCRReason.LoadingError, IllegalStateException("Session not initialized"))
        val env = ortEnvRef.load()
            ?: throw OCRException(OCRReason.LoadingError, IllegalStateException("Environment not initialized"))
        val dictionary = dictionaryRef.load()
        if (dictionary.isEmpty()) {
            throw OCRException(OCRReason.LoadingError, IllegalStateException("Dictionary not loaded"))
        }

        // Preprocess image
        val inputTensor = preprocessImage(inputImage, env)

        // Run inference
        val output = try {
            session.run(mapOf("x" to inputTensor))
        } finally {
            inputTensor.close()
        }

        return try {
            val outputTensor = output.get(0).value as? Array<*>
                ?: throw OCRException(
                    OCRReason.LoadingError,
                    cause = IllegalStateException("Unexpected recognition output type"),
                )

            // Decode result using CTC decoding
            ctcDecode(outputTensor, dictionary)
        } finally {
            output.close()
        }
    }

    /**
     * Preprocesses an image for the PaddleOCR recognition model.
     *
     * Matches the resize_norm_img function from PaddleOCR Python:
     * - **Source**: https://github.com/PaddlePaddle/PaddleOCR/blob/main/ppocr/data/imaug/rec_img_aug.py
     *
     * Steps:
     * 1. Convert bitmap to OpenCV Mat (RGB)
     * 2. Resize maintaining aspect ratio with target height of 48
     * 3. Normalize using (x/255 - 0.5) / 0.5 to get [-1, 1] range
     * 4. Pad to target width (320) with zeros
     * 5. Convert to NCHW format (batch, channels, height, width)
     *
     * @param inputImage CvImage (cropped text region)
     * @param env ONNX Runtime environment
     * @return OnnxTensor ready for inference
     */
    private fun preprocessImage(inputImage: CvImage, env: OrtEnvironment): OnnxTensor {
        if (inputImage.isEmpty()) {
            throw OCRException(
                OCRReason.LoadingError,
                cause = IllegalArgumentException("Input image is empty"),
            )
        }
        val h = inputImage.height
        val w = inputImage.width

        // Calculate aspect ratio and resized width
        val ratio = w.toDouble() / h.toDouble()
        val resizedW = if (ceil(TARGET_HEIGHT * ratio).toInt() > TARGET_WIDTH) {
            TARGET_WIDTH
        } else {
            ceil(TARGET_HEIGHT * ratio).toInt()
        }

        // Resize image using CvImage public API
        val resizedImage = inputImage.resizeTo(TARGET_HEIGHT, resizedW)

        val floatImage = try {
            // Convert to float type for preprocessing
            resizedImage.convertToFloat()
        } finally {
            resizedImage.close()
        }

        // Create padded buffer in NCHW format (already zero-initialized)
        val buffer = FloatBuffer.allocate(1 * CHANNELS * TARGET_HEIGHT * TARGET_WIDTH)

        try {
            // Convert HWC to NCHW and normalize: (x/255 - 0.5) / 0.5
            for (c in 0 until CHANNELS) {
                for (y in 0 until TARGET_HEIGHT) {
                    for (x in 0 until resizedW) {
                        val pixel = floatImage.getPixel(y, x)
                        if (pixel != null) {
                            // Normalization: (pixel/255 - 0.5) / 0.5
                            val normalized = (pixel[c].toFloat() / 255.0f - 0.5f) / 0.5f
                            val index = c * TARGET_HEIGHT * TARGET_WIDTH + y * TARGET_WIDTH + x
                            buffer.put(index, normalized)
                        }
                    }
                }
            }
        } finally {
            floatImage.close()
        }

        buffer.rewind()

        return OnnxTensor.createTensor(
            env,
            buffer,
            longArrayOf(1, CHANNELS.toLong(), TARGET_HEIGHT.toLong(), TARGET_WIDTH.toLong()),
        )
    }

    /**
     * Decodes the model output using CTC (Connectionist Temporal Classification) greedy decoding.
     *
     * Matches the CTCLabelDecode postprocess function from PaddleOCR Python:
     * - **Source**: https://github.com/PaddlePaddle/PaddleOCR/blob/main/ppocr/postprocess/rec_postprocess.py
     *
     * Steps:
     * 1. Get argmax for each timestep
     * 2. Skip blank tokens (index 0)
     * 3. Skip consecutive duplicate indices
     *
     * @param output Raw model output with shape [1, seq_len, num_classes]
     * @param dictionary Character dictionary (index -> character)
     * @return Decoded text string
     */
    @Suppress("UNCHECKED_CAST")
    private fun ctcDecode(output: Array<*>, dictionary: Map<Int, String>): RecognitionResult {
        val charList = mutableListOf<String>()
        val confidences = mutableListOf<Float>()

        // Output shape is [1, seq_len, num_classes]
        val batchOutput = output.firstOrNull() as? Array<*>
            ?: throw OCRException(
                OCRReason.LoadingError,
                cause = IllegalStateException("Recognition output missing batch dimension"),
            )

        var prevIdx = -1

        for (timestepRaw in batchOutput) {
            val timestep = timestepRaw as? FloatArray ?: continue
            if (timestep.isEmpty()) continue

            // Find the index with maximum logit (argmax)
            var maxIdx = 0
            var maxVal = timestep[0]
            for (i in 1 until timestep.size) {
                if (timestep[i] > maxVal) {
                    maxVal = timestep[i]
                    maxIdx = i
                }
            }

            // CTC decoding: skip blanks (0) and consecutive duplicates
            if (maxIdx != prevIdx && maxIdx != 0) {
                dictionary[maxIdx]?.let { char ->
                    charList.add(char)
                    // Compute softmax probability for this timestep's max class
                    confidences.add(softmaxMax(timestep, maxIdx))
                }
            }

            prevIdx = maxIdx
        }

        val text = charList.joinToString("")
        val score = if (confidences.isEmpty()) 0f else confidences.average().toFloat()
        return RecognitionResult(text, score)
    }

    private fun softmaxMax(logits: FloatArray, maxIdx: Int): Float {
        val maxLogit = logits[maxIdx]
        var sumExp = 0.0
        for (v in logits) {
            sumExp += kotlin.math.exp((v - maxLogit).toDouble())
        }
        return (1.0 / sumExp).toFloat()
    }

    /**
     * Cleans up ONNX resources.
     */
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

        dictionaryRef.store(emptyMap())
        isInitialized.store(false)
    }

    override fun close() {
        if (!isClosed.compareAndSet(false, true)) {
            return
        }

        try {
            runBlocking {
                mutex.withLock {
                    withTimeout(15.seconds) {
                        cleanup()
                    }
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, TAG) { "Error closing PaddleOCR recognition: ${e.asLog()}" }
        }
    }
}

private const val TAG = "PaddleOcrRecognition"
