package com.travelexpenses.ocr

/**
 * Platform-specific OCR engine abstraction.
 *
 * Each platform implements this using its native OCR:
 * - Android: Google ML Kit Text Recognition
 * - iOS: Apple Vision VNRecognizeTextRequest
 *
 * The engine extracts raw text from an image. The [OcrParser] then
 * processes the raw text into a structured [OcrResult].
 */
interface OcrEngine {
    /**
     * Extract text from an image at the given file path.
     *
     * @param imagePath absolute path to the image file
     * @return raw extracted text, or null if OCR fails
     */
    suspend fun recognizeText(imagePath: String): OcrTextResult?
}

/**
 * Raw text recognition result from the platform OCR engine.
 */
data class OcrTextResult(
    val fullText: String,
    val blocks: List<TextBlock> = emptyList(),
)

/**
 * A block of text recognized by the OCR engine, with position info.
 */
data class TextBlock(
    val text: String,
    val confidence: Float? = null,
)
