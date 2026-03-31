package com.github.arthurkun.koo.imaging

/**
 * Platform-agnostic wrapper for native image matrix operations.
 * This expect class is implemented differently on each platform:
 * - Android: Uses OpenCV Mat
 * - JVM: Uses bytedeco OpenCV Mat
 *
 * NativeMat is internal to the module. External code should use [CvImage] instead.
 */
internal expect class NativeMat : CvImage {
    override val tag: String

    override val width: Int
    override val height: Int

    override fun isEmpty(): Boolean

    override fun close()

    override fun resizeTo(targetHeight: Int, targetWidth: Int): NativeMat

    override fun toRgbCvImage(): NativeMat

    override fun getPixel(y: Int, x: Int): DoubleArray?

    override fun convertToFloat(): NativeMat

    companion object {
        suspend fun fromByteArray(
            byteArray: ByteArray,
            isColor: Boolean,
            tag: String = "",
        ): NativeMat
    }
}
