package com.github.arthurkun.koo.recognition

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Shared loader for PaddleOCR recognition model bytes and dictionaries.
 *
 * The loader owns the cache policy so future model modules only need to provide raw model
 * and dictionary bytes via [RecognitionModel].
 */
public class RecognitionModelLoader public constructor(
    public val model: RecognitionModel,
    public val cachePolicy: RecognitionModelCachePolicy = RecognitionModelCachePolicy.KEEP_IN_MEMORY,
) : AutoCloseable {

    private val mutex = Mutex()
    private var cachedModelBytes: ByteArray? = null
    private var cachedDictionary: Map<Int, String>? = null

    /** Loads ONNX model bytes according to [cachePolicy]. */
    public suspend fun loadModelBytes(): ByteArray {
        return mutex.withLock {
            when (cachePolicy) {
                RecognitionModelCachePolicy.KEEP_IN_MEMORY -> {
                    cachedModelBytes ?: model.loadModelBytes().also { cachedModelBytes = it }
                }

                RecognitionModelCachePolicy.LOAD_EACH_TIME -> model.loadModelBytes()
            }
        }
    }

    /** Loads and parses the PaddleOCR dictionary according to [cachePolicy]. */
    public suspend fun loadDictionary(): Map<Int, String> {
        return mutex.withLock {
            when (cachePolicy) {
                RecognitionModelCachePolicy.KEEP_IN_MEMORY -> {
                    cachedDictionary ?: parseDictionary(model.loadDictionaryBytes()).also { cachedDictionary = it }
                }

                RecognitionModelCachePolicy.LOAD_EACH_TIME -> parseDictionary(model.loadDictionaryBytes())
            }
        }
    }

    /** Drops any retained model bytes or parsed dictionary. */
    public fun clear() {
        cachedModelBytes = null
        cachedDictionary = null
    }

    override fun close() {
        clear()
    }

    private fun parseDictionary(dictionaryBytes: ByteArray): Map<Int, String> {
        val charDict = mutableMapOf<Int, String>()
        charDict[0] = "blank"

        var index = 1
        dictionaryBytes.decodeToString().lineSequence().forEach { line ->
            if (line.isNotEmpty()) {
                charDict[index] = line
                index++
            }
        }

        charDict[index] = " "
        return charDict
    }
}
