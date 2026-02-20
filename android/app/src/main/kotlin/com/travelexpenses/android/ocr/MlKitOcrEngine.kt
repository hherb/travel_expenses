package com.travelexpenses.android.ocr

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.travelexpenses.ocr.OcrEngine
import com.travelexpenses.ocr.OcrTextResult
import com.travelexpenses.ocr.TextBlock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Android ML Kit implementation of [OcrEngine].
 * Uses on-device text recognition (no network required).
 */
class MlKitOcrEngine : OcrEngine {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognizeText(imagePath: String): OcrTextResult? {
        return suspendCancellableCoroutine { cont ->
            try {
                val bitmap = BitmapFactory.decodeFile(imagePath)
                if (bitmap == null) {
                    cont.resume(null)
                    return@suspendCancellableCoroutine
                }

                val inputImage = InputImage.fromBitmap(bitmap, 0)

                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        val blocks = visionText.textBlocks.map { block ->
                            TextBlock(
                                text = block.text,
                                confidence = block.lines.firstOrNull()?.confidence,
                            )
                        }
                        cont.resume(
                            OcrTextResult(
                                fullText = visionText.text,
                                blocks = blocks,
                            )
                        )
                    }
                    .addOnFailureListener {
                        cont.resume(null)
                    }
            } catch (_: Exception) {
                cont.resume(null)
            }
        }
    }
}
