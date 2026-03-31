package com.github.arthurkun.koo.imaging

public interface CvImage : AutoCloseable {
    public val width: Int
    public val height: Int
    public val tag: String

    /**
     * Returns whether this image is empty (has no data).
     */
    public fun isEmpty(): Boolean

    /**
     * Resizes the image to the specified dimensions (synchronous).
     *
     * @param targetHeight Target height
     * @param targetWidth Target width
     * @return New resized CvImage
     */
    public fun resizeTo(targetHeight: Int, targetWidth: Int): CvImage

    /**
     * Converts this CvImage to RGB format.
     */
    public fun toRgbCvImage(): CvImage

    /**
     * Gets the pixel values at a specific position.
     * Returns an array of channel values (BGR or grayscale), or null if the position is invalid.
     *
     * @param y the row (y-coordinate)
     * @param x the column (x-coordinate)
     * @return pixel channel values as [DoubleArray], or null
     */
    public fun getPixel(y: Int, x: Int): DoubleArray?

    /**
     * Converts the image to float type for preprocessing.
     *
     * @return New CvImage with float pixel type
     */
    public fun convertToFloat(): CvImage

    public companion object {
        /**
         * Creates a CvImage from a byte array (image data).
         *
         * @param byteArray The raw image bytes
         * @param isColor Whether to decode as color (true) or grayscale (false)
         * @param tag Optional tag for identification
         * @return A new CvImage instance created from the byte array
         */
        public suspend fun fromByteArray(
            byteArray: ByteArray,
            isColor: Boolean,
            tag: String = "",
        ): CvImage = NativeMat.Companion.fromByteArray(byteArray, isColor, tag)
    }
}
