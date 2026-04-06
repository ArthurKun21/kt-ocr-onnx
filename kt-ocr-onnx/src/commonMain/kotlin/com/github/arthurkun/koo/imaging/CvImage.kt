package com.github.arthurkun.koo.imaging

internal interface CvImage : AutoCloseable {
    val width: Int
    val height: Int
    val tag: String

    fun isEmpty(): Boolean

    fun resizeTo(targetHeight: Int, targetWidth: Int): CvImage

    fun toRgbCvImage(): CvImage

    fun getPixel(y: Int, x: Int): DoubleArray

    fun convertToFloat(): CvImage

    companion object {
        suspend fun fromByteArray(
            byteArray: ByteArray,
            isColor: Boolean,
            tag: String = "",
        ): CvImage = NativeMat.Companion.fromByteArray(byteArray, isColor, tag)
    }
}
