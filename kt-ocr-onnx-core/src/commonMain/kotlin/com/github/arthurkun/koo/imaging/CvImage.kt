package com.github.arthurkun.koo.imaging

import com.github.arthurkun.koo.InternalKtOcrONNXApi

@InternalKtOcrONNXApi
public interface CvImage : AutoCloseable {
    public val width: Int
    public val height: Int
    public val tag: String

    public fun isEmpty(): Boolean

    public fun resizeTo(targetHeight: Int, targetWidth: Int): CvImage

    public fun toRgbCvImage(): CvImage

    public fun toBgrCvImage(): CvImage

    public fun getPixel(y: Int, x: Int): DoubleArray

    public fun convertToFloat(): CvImage

    public companion object {
        public suspend fun fromByteArray(
            byteArray: ByteArray,
            isColor: Boolean,
            tag: String = "",
        ): CvImage = NativeMat.Companion.fromByteArray(byteArray, isColor, tag)
    }
}
