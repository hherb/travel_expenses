package com.travelexpenses.android.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Preprocesses receipt images before OCR to improve text recognition accuracy.
 * Applies contrast enhancement and grayscale conversion.
 */
object ImagePreprocessor {

    private const val TAG = "ImagePreprocessor"

    /**
     * Preprocesses an image file for improved OCR accuracy.
     * - Converts to grayscale
     * - Enhances contrast
     *
     * Writes the result to a temporary file to preserve the original.
     *
     * @param imagePath path to the image file
     * @return path to the preprocessed temp file, or the original path if processing failed
     */
    fun preprocess(imagePath: String): String {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(imagePath, options)

            val maxDim = maxOf(options.outWidth, options.outHeight)
            val sampleSize = if (maxDim > 4096) (maxDim / 4096) else 1

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inMutable = false
            }
            val original = BitmapFactory.decodeFile(imagePath, decodeOptions)
            if (original == null) {
                Log.w(TAG, "Failed to decode image: $imagePath")
                return imagePath
            }

            val processed = enhanceForOcr(original)
            original.recycle()

            val sourceFile = File(imagePath)
            val tempFile = File(sourceFile.parent, "ocr_preprocessed_${sourceFile.name}")
            FileOutputStream(tempFile).use { out ->
                processed.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            processed.recycle()

            tempFile.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "Image preprocessing failed, using original", e)
            imagePath
        }
    }

    /**
     * Applies grayscale conversion and contrast enhancement to a bitmap.
     */
    private fun enhanceForOcr(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val contrastFactor = 1.5f
        val translate = (-0.5f * contrastFactor + 0.5f) * 255f

        val colorMatrix = ColorMatrix(
            floatArrayOf(
                0.299f * contrastFactor, 0.587f * contrastFactor, 0.114f * contrastFactor, 0f, translate,
                0.299f * contrastFactor, 0.587f * contrastFactor, 0.114f * contrastFactor, 0f, translate,
                0.299f * contrastFactor, 0.587f * contrastFactor, 0.114f * contrastFactor, 0f, translate,
                0f, 0f, 0f, 1f, 0f,
            )
        )

        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(source, 0f, 0f, paint)

        return result
    }
}
