package com.github.arthurkun.koo

/**
 * Exception thrown when an error occurs during the OCR process.
 *
 * @param message A human-readable error message describing the OCR failure.
 * @param cause The underlying cause of the exception, if any.
 */
public open class OCRException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Model setup failed, including dictionary, ONNX environment, or session creation.
 */
public class OCRInitializationException(
    message: String,
    cause: Throwable? = null,
) : OCRException(message, cause)

/**
 * OCR operation attempted after a service or model has already been closed.
 */
public class OCRClosedException(
    message: String,
    cause: Throwable? = null,
) : OCRException(message, cause)

/**
 * Input/output or resource access failed while loading OCR assets or inputs.
 */
public class OCRIOException(
    message: String,
    cause: Throwable? = null,
) : OCRException(message, cause)

/**
 * Raw bytes could not be decoded into a valid image.
 */
public class OCRImageDecodeException(
    message: String,
    cause: Throwable? = null,
) : OCRException(message, cause)

/**
 * Image transformation or pixel access failed.
 */
public class OCRImageProcessingException(
    message: String,
    cause: Throwable? = null,
) : OCRException(message, cause)

/**
 * Generic runtime inference failure while executing OCR.
 */
public open class OCRInferenceException(
    message: String,
    cause: Throwable? = null,
) : OCRException(message, cause)

/**
 * Inference prerequisites are missing or invalid at runtime.
 */
public class OCRModelStateException(
    message: String,
    cause: Throwable? = null,
) : OCRInferenceException(message, cause)

/**
 * Model output has an unexpected shape or type.
 */
public class OCRModelOutputException(
    message: String,
    cause: Throwable? = null,
) : OCRInferenceException(message, cause)
