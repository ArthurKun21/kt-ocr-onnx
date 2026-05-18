package com.github.arthurkun.koo

import com.github.arthurkun.koo.recognition.RecognitionModel
import com.github.arthurkun.koo.recognition.RecognitionModelCachePolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public class RecognitionModelManager public constructor(
    private val scope: CoroutineScope,
    private val cachePolicy: RecognitionModelCachePolicy,
    private val isOpen: () -> Boolean,
) {
    private val mutex = Mutex()
    private val cachedRecognitions = mutableMapOf<String, PaddleOcrRecognition>()

    public suspend fun <T> withRecognition(
        recognitionModel: RecognitionModel,
        block: suspend (PaddleOcrRecognition) -> T,
    ): T {
        if (cachePolicy == RecognitionModelCachePolicy.LOAD_EACH_TIME) {
            val recognition = mutex.withLock {
                checkOpen()
                createRecognition(recognitionModel)
            }
            return try {
                block(recognition)
            } finally {
                recognition.closeSuspending()
            }
        }

        val recognition = mutex.withLock {
            checkOpen()
            cachedRecognitions.getOrPut(recognitionModel.id) {
                createRecognition(recognitionModel)
            }
        }
        return block(recognition)
    }

    public suspend fun close() {
        mutex.withLock {
            cachedRecognitions.values.forEach { it.closeSuspending() }
            cachedRecognitions.clear()
        }
    }

    private fun createRecognition(recognitionModel: RecognitionModel): PaddleOcrRecognition {
        return PaddleOcrRecognition(scope, recognitionModel)
    }

    private fun checkOpen() {
        if (!isOpen()) {
            throw OCRClosedException("OCR service is already closed")
        }
    }
}
