package com.sirim.scanner.data.validation

import com.sirim.scanner.data.ocr.FieldConfidence
import com.sirim.scanner.data.ocr.FieldNote

data class ValidationResult(
    val sanitized: Map<String, FieldConfidence>,
    val errors: Map<String, String>,
    val warnings: Map<String, String>
)

object FieldValidator {

    // TEA + 7 digits  OR  T + 9 digits  (case-insensitive)
    private val serialRegex = Regex("""^(TEA\d{7}|T\d{9})$""", RegexOption.IGNORE_CASE)

    // Uppercase letters, digits, dash; up to 200 chars
    private val batchRegex = Regex("""^[A-Z0-9-]{1,200}$""", RegexOption.IGNORE_CASE)

    // Alphanumeric plus space and a few punctuation marks; put '-' at end to avoid ranges
    private val alphaNumeric = Regex("""^[A-Za-z0-9 .,&'/-]{1,1500}$""")

    // Either an SAE oil rating like "SAE 10W-40" OR fall back to the relaxed alphanumeric
    private val ratingRegex = Regex(
        """^(SAE\s*[0-9]{1,2}W-?[0-9]{1,2}|[A-Za-z0-9 .,&'/-]{1,600})$""",
        RegexOption.IGNORE_CASE
    )

    // Number + optional whitespace + unit
    private val sizeRegex = Regex("""^[0-9]+\s*(L|ML|LITRE|LTR|KG)$""", RegexOption.IGNORE_CASE)

    fun validate(fields: Map<String, FieldConfidence>): ValidationResult {
        val sanitized = mutableMapOf<String, FieldConfidence>()
        val errors = mutableMapOf<String, String>()
        val warnings = mutableMapOf<String, String>()

        fields.forEach { (key, confidence) ->
            val trimmed = confidence.value.trim()
            val updated = confidence.copy(value = trimmed)

            when (key) {
                "sirimSerialNo" -> handleSerial(updated, sanitized, errors, warnings)
                "batchNo" -> handlePattern(updated, batchRegex, sanitized, warnings, key)
                "brandTrademark", "model", "type" -> handlePattern(updated, alphaNumeric, sanitized, warnings, key)
                "rating" -> handlePattern(updated, ratingRegex, sanitized, warnings, key)
                "size" -> handlePattern(updated, sizeRegex, sanitized, warnings, key)
                else -> sanitized[key] = updated
            }

            val notes = sanitized[key]?.notes ?: return@forEach
            if (FieldNote.CONFLICT in notes) {
                warnings.putIfAbsent(key, "Mismatch between QR data and OCR text")
            }
            if (FieldNote.CORRECTED_CHARACTER in notes && warnings[key] == null) {
                warnings[key] = "Similar characters corrected (O↔0, I↔1, S↔5)"
            }
            if (FieldNote.LENGTH_TRIMMED in notes) {
                warnings[key] = "Value truncated to fit allowed length"
            }
            if (FieldNote.PATTERN_RELAXED in notes && warnings[key] == null) {
                warnings[key] = "Field matched using relaxed pattern"
            }
        }

        if (!sanitized.containsKey("sirimSerialNo")) {
            errors["sirimSerialNo"] = "Serial number missing or unreadable"
        }

        return ValidationResult(
            sanitized = sanitized,
            errors = errors,
            warnings = warnings
        )
    }

    private fun handleSerial(
        confidence: FieldConfidence,
        sanitized: MutableMap<String, FieldConfidence>,
        errors: MutableMap<String, String>,
        warnings: MutableMap<String, String>
    ) {
        val normalized = confidence.value.uppercase()
        val cleaned = normalized.replace("-", "").replace(" ", "")
        if (serialRegex.matches(cleaned)) {
            sanitized["sirimSerialNo"] = confidence.copy(value = cleaned)
        } else {
            val newNotes = confidence.notes + FieldNote.FORMAT_MISMATCH
            sanitized["sirimSerialNo"] =
                confidence.copy(value = cleaned, confidence = confidence.confidence * 0.6f, notes = newNotes)
            warnings["sirimSerialNo"] = "Serial number format could not be verified"
        }
    }

    private fun handlePattern(
        confidence: FieldConfidence,
        regex: Regex,
        sanitized: MutableMap<String, FieldConfidence>,
        warnings: MutableMap<String, String>,
        key: String
    ) {
        if (confidence.value.isEmpty()) return
        val matches = regex.matches(confidence.value)
        val updated = if (matches) {
            confidence
        } else {
            confidence.copy(
                confidence = confidence.confidence * 0.75f,
                notes = confidence.notes + FieldNote.FORMAT_MISMATCH
            )
        }
        sanitized[key] = updated
        if (!matches) warnings[key] = "${prettyFieldName(key)} format may be invalid"
    }

    private fun prettyFieldName(field: String): String =
        field.replace(Regex("([A-Z])")) { " " + it.value.lowercase() }
            .trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
