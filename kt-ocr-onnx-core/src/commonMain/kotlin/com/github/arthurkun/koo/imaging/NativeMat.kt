package com.github.arthurkun.koo.imaging

/**
 * Platform-agnostic wrapper for native image matrix operations.
 * This expect class is implemented differently on each platform:
 * - Android: Uses OpenCV Mat
 * - JVM: Uses bytedeco OpenCV Mat
 *
 * NativeMat is internal to the module. External code should use [CvImage] instead.
 */
public expect class NativeMat : CvImage {
    public override val tag: String

    public override val width: Int
    public override val height: Int

    public override fun isEmpty(): Boolean

    public override fun close()

    public override fun resizeTo(targetHeight: Int, targetWidth: Int): NativeMat

    public override fun toRgbCvImage(): NativeMat

    public override fun getPixel(y: Int, x: Int): DoubleArray

    public override fun convertToFloat(): NativeMat

    public companion object {
        public suspend fun fromByteArray(
            byteArray: ByteArray,
            isColor: Boolean,
            tag: String = "",
        ): NativeMat
    }
}
