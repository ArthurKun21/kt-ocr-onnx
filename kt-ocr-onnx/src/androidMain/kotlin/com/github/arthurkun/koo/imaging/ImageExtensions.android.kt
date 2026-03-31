package com.github.arthurkun.koo.imaging

import org.opencv.core.Mat

internal inline fun <T : Mat, R> T.use(block: (T) -> R): R = try {
    block(this)
} finally {
    release()
}
