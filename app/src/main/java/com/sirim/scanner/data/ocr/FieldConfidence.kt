package com.sirim.scanner.data.ocr

import kotlin.math.max

/**
 * Represents a field extracted from the OCR/QR pipeline with a confidence score and source.
 */
data class FieldConfidence(
    val value: String,
    val confidence: Float,
    val source: FieldSource,
    val notes: Set<FieldNote> = emptySet()
) {
    fun mergeWith(other: FieldConfidence): FieldConfidence {
        val dominant = if (confidence >= other.confidence) this else other
        val combinedConfidence = max(confidence, other.confidence)
        val combinedNotes = notes + other.notes + if (value != other.value) setOf(FieldNote.CONFLICT) else emptySet()
        return dominant.copy(confidence = combinedConfidence, notes = combinedNotes)
    }
}

enum class FieldSource {
    OCR,
    QR,
    USER
}

enum class FieldNote {
    CORRECTED_CHARACTER,
    PATTERN_RELAXED,
    LENGTH_TRIMMED,
    FORMAT_MISMATCH,
    CONFLICT,
    VERIFIED_BY_MULTIPLE_SOURCES,
    CONFLICTING_SOURCES
}
