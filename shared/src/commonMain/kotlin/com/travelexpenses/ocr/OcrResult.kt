package com.travelexpenses.ocr

import kotlinx.serialization.Serializable

/**
 * Structured result of OCR field extraction from receipt text.
 * Each field has an optional confidence score (0.0-1.0).
 */
@Serializable
data class OcrResult(
    val vendor: String? = null,
    val date: String? = null,         // ISO 8601 date string (yyyy-MM-dd)
    val currency: String? = null,     // ISO 4217 currency code
    val total: String? = null,        // String-encoded decimal
    val tax: String? = null,          // String-encoded decimal
    val confidence: FieldConfidence = FieldConfidence(),
)

/**
 * Per-field confidence scores for OCR extraction.
 * 0.0 = no confidence, 1.0 = high confidence, null = field not extracted.
 */
@Serializable
data class FieldConfidence(
    val vendor: Float? = null,
    val date: Float? = null,
    val currency: Float? = null,
    val total: Float? = null,
    val tax: Float? = null,
)
