package com.github.arthurkun.koo

/**
 * Exception thrown when an error occurs during the OCR process.
 *
 * @property reason The specific reason why the OCR operation failed.
 * @param cause The underlying cause of the exception, if any.
 */
public class OCRException(
    public val reason: OCRReason,
    cause: Throwable? = null,
) : Exception(reason.message, cause)

public sealed class OCRReason(public val message: String) {
    /**
     * Indicates that the OCR model failed to initialize.
     */
    public data object InitializationError : OCRReason("OCR model initialization failed")

    /**
     * Indicates that an error occurred while attempting to load the OCR model into memory.
     */
    public data object LoadingError : OCRReason("Error loading OCR model")
}
