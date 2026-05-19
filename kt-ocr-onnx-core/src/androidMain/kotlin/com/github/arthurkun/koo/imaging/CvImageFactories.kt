package com.github.arthurkun.koo.imaging

import android.graphics.Bitmap
import com.github.arthurkun.koo.InternalKtOcrONNXApi

/**
 * Creates a [CvImage] from an Android [Bitmap].
 *
 * @param bitmap the source bitmap
 * @param tag optional tag for identification
 * @return a new [CvImage] instance
 */
@InternalKtOcrONNXApi
public fun cvImageFromBitmap(bitmap: Bitmap, tag: String = ""): CvImage =
    NativeMat.fromBitmap(bitmap, tag)
