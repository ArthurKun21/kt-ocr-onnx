package com.github.arthurkun.koo.imaging

import android.graphics.Bitmap

/**
 * Creates a [CvImage] from an Android [Bitmap].
 *
 * @param bitmap the source bitmap
 * @param tag optional tag for identification
 * @return a new [CvImage] instance
 */
internal fun cvImageFromBitmap(bitmap: Bitmap, tag: String = ""): CvImage =
    NativeMat.fromBitmap(bitmap, tag)
