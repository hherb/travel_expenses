package com.travelexpenses.ocr

import kotlinx.serialization.Serializable

/**
 * Confidence score for a single OCR-extracted field.
 * Range: 0.0 (no confidence / not found) to 1.0 (high confidence).
 */
typealias Confidence = Float

/**
 * Structured result of parsing raw OCR text from a receipt.
 * Each field has an associated confidence score indicating extraction reliability.
 */
@Serializable
data class OcrResult(
    val vendor: String? = null,
    val vendorConfidence: Confidence = 0f,
    val date: String? = null,           // ISO 8601 (YYYY-MM-DD) if successfully parsed
    val dateConfidence: Confidence = 0f,
    val currency: String? = null,       // ISO 4217 code
    val currencyConfidence: Confidence = 0f,
    val total: String? = null,          // String-encoded decimal
    val totalConfidence: Confidence = 0f,
    val tax: String? = null,            // String-encoded decimal
    val taxConfidence: Confidence = 0f,
    val rawText: String = "",           // Original OCR text for reference
)
