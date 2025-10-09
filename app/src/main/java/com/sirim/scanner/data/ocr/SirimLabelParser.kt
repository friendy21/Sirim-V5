package com.sirim.scanner.data.ocr

import com.sirim.scanner.analytics.ScanAnalytics
import com.sirim.scanner.analytics.SerialExtractionEvent
import com.sirim.scanner.analytics.SerialVerificationEvent
import java.util.Locale
import kotlin.math.min

/**
 * Parser responsible for extracting structured data from OCR and QR sources on
 * SIRIM certification labels. The implementation is tuned specifically for the
 * label layout (T + 9 digit serial under the QR code) and provides dual-source
 * verification to maximise confidence when both OCR and QR agree.
 */
object SirimLabelParser {

    private const val SERIAL_FIELD_KEY = "sirimSerialNo"

    private const val EXACT_SERIAL_CONFIDENCE = 0.90f
    private const val RELAXED_SERIAL_CONFIDENCE = 0.85f
    private const val LEGACY_SERIAL_CONFIDENCE = 0.78f
    private const val QR_BASE_CONFIDENCE = 0.98f
    private const val VERIFIED_CONFIDENCE = 0.99f
    private const val CONFLICT_CONFIDENCE = 0.90f

    private const val PRIMARY_FIELD_CONFIDENCE = 0.75f
    private const val SECONDARY_FIELD_CONFIDENCE = 0.65f
    private const val TERTIARY_FIELD_CONFIDENCE = 0.60f

    private const val CORRECTION_PENALTY = 0.03f
    private const val LENGTH_PENALTY = 0.05f
    private const val MIN_CONFIDENCE = 0.10f
    private const val MAX_FIELD_CONFIDENCE = 0.95f

    private val SIRIM_SERIAL_EXACT = Regex("(?<![A-Z0-9])T\\d{9}(?![A-Z0-9])", RegexOption.IGNORE_CASE)
    private val SIRIM_SERIAL_RELAXED = Regex("(?<![A-Z0-9])T[\\s-]?(\\d{9})(?![A-Z0-9])", RegexOption.IGNORE_CASE)
    private val TEA_SERIAL_PATTERN = Regex("(?<![A-Z0-9])TEA[\\s-]?(\\d{7})(?![A-Z0-9])", RegexOption.IGNORE_CASE)

    private val characterCorrections = mapOf(
        'O' to '0',
        'o' to '0',
        'I' to '1',
        'i' to '1',
        'l' to '1',
        'S' to '5',
        's' to '5',
        'B' to '8',
        'b' to '8',
        'Z' to '2',
        'z' to '2',
        'G' to '6',
        'g' to '6',
        'D' to '0',
        'd' to '0'
    )

    private data class FieldPatternSpec(
        val regex: Regex,
        val baseConfidence: Float,
        val allowCorrections: Boolean = false,
        val uppercaseResult: Boolean = false,
        val defaultNotes: Set<FieldNote> = emptySet()
    )

    private val fieldPatternSpecs: Map<String, List<FieldPatternSpec>> = mapOf(
        "batchNo" to listOf(
            FieldPatternSpec(
                regex = Regex("(?:Batch)\\s*(?:No\\.?|Number)?\\s*[:\\-]?\\s*([A-Z0-9][A-Z0-9\\-]{2,199})", RegexOption.IGNORE_CASE),
                baseConfidence = PRIMARY_FIELD_CONFIDENCE
            ),
            FieldPatternSpec(
                regex = Regex("\\b([A-Z]{2,4}-\\d{4,})\\b"),
                baseConfidence = SECONDARY_FIELD_CONFIDENCE,
                defaultNotes = setOf(FieldNote.PATTERN_RELAXED)
            )
        ),
        "brandTrademark" to listOf(
            FieldPatternSpec(
                regex = Regex("(?:Brand)\\s*/\\s*(?:Trademark)\\s*[:\\-]?\\s*([A-Z0-9][A-Z0-9 &'\\-]{1,1023})", RegexOption.IGNORE_CASE),
                baseConfidence = PRIMARY_FIELD_CONFIDENCE
            ),
            FieldPatternSpec(
                regex = Regex("(?:Brand|Trademark)\\s*[:\\-]?\\s*([A-Z0-9][A-Z0-9 &'\\-]{1,1023})", RegexOption.IGNORE_CASE),
                baseConfidence = SECONDARY_FIELD_CONFIDENCE,
                defaultNotes = setOf(FieldNote.PATTERN_RELAXED)
            )
        ),
        "model" to listOf(
            FieldPatternSpec(
                regex = Regex("(?:Model)\\s*[:\\-]?\\s*([A-Za-z0-9][A-Za-z0-9\\-\\s/]{1,1499})", RegexOption.IGNORE_CASE),
                baseConfidence = PRIMARY_FIELD_CONFIDENCE
            )
        ),
        "type" to listOf(
            FieldPatternSpec(
                regex = Regex("(?:Type)\\s*[:\\-]?\\s*([A-Za-z0-9][A-Za-z0-9\\-\\s/]{1,1499})", RegexOption.IGNORE_CASE),
                baseConfidence = PRIMARY_FIELD_CONFIDENCE
            )
        ),
        "rating" to listOf(
            FieldPatternSpec(
                regex = Regex("(?:Rating)\\s*[:\\-]?\\s*([A-Za-z0-9][A-Za-z0-9\\-\\s/]{1,599})", RegexOption.IGNORE_CASE),
                baseConfidence = PRIMARY_FIELD_CONFIDENCE
            ),
            FieldPatternSpec(
                regex = Regex("\\b(SAE\\s*\\d{1,2}W-?\\d{1,2})\\b", RegexOption.IGNORE_CASE),
                baseConfidence = SECONDARY_FIELD_CONFIDENCE,
                defaultNotes = setOf(FieldNote.PATTERN_RELAXED),
                uppercaseResult = true
            ),
            FieldPatternSpec(
                regex = Regex("\\b(API\\s*[A-Z]{2}(?:-\\d+)?)\\b", RegexOption.IGNORE_CASE),
                baseConfidence = TERTIARY_FIELD_CONFIDENCE,
                defaultNotes = setOf(FieldNote.PATTERN_RELAXED),
                uppercaseResult = true
            )
        ),
        "size" to listOf(
            FieldPatternSpec(
                regex = Regex("(?:Size)\\s*[:\\-]?\\s*(\\d+\\s*(?:L|ML|LITRE|LTR|KG))\\b", RegexOption.IGNORE_CASE),
                baseConfidence = PRIMARY_FIELD_CONFIDENCE,
                uppercaseResult = true
            ),
            FieldPatternSpec(
                regex = Regex("\\b(\\d+\\s*(?:L|ML|LITRE|LTR|KG))\\b", RegexOption.IGNORE_CASE),
                baseConfidence = SECONDARY_FIELD_CONFIDENCE,
                uppercaseResult = true,
                defaultNotes = setOf(FieldNote.PATTERN_RELAXED)
            )
        )
    )

    private val fieldPatterns: Map<String, List<Regex>> = fieldPatternSpecs.mapValues { entry ->
        entry.value.map(FieldPatternSpec::regex)
    }

    fun parse(text: String): Map<String, FieldConfidence> {
        if (text.isBlank()) return emptyMap()

        val normalized = normalizeText(text)
        val candidates = mutableMapOf<String, FieldConfidence>()

        extractSerial(normalized)?.let { serial ->
            candidates[SERIAL_FIELD_KEY] = serial
        }

        fieldPatternSpecs.forEach { (field, specs) ->
            specLoop@ for (spec in specs) {
                val match = spec.regex.find(normalized) ?: continue
                val raw = match.groupValues.lastOrNull()?.trim().orEmpty()
                if (raw.isEmpty()) continue

                val candidate = buildCandidate(field, raw, spec)
                val existing = candidates[field]
                if (existing == null || candidate.confidence > existing.confidence) {
                    candidates[field] = candidate
                }
                break@specLoop
            }
        }

        return candidates
    }

    fun mergeWithQr(
        current: MutableMap<String, FieldConfidence>,
        qrPayload: String?
    ): FieldConfidence? {
        val qrSerial = qrPayload?.let { payload ->
            SIRIM_SERIAL_EXACT.find(payload)?.value
        } ?: return null

        val (correctedSerial, correctionNotes) = correctCharacters(
            value = qrSerial,
            uppercase = true,
            enableCorrections = true
        )
        val correctionPenalty = if (correctionNotes.isNotEmpty()) CORRECTION_PENALTY else 0f
        val qrConfidenceValue = (QR_BASE_CONFIDENCE - correctionPenalty)
            .coerceIn(MIN_CONFIDENCE, VERIFIED_CONFIDENCE)
        val baseNotes = correctionNotes.toMutableSet()

        val existing = current[SERIAL_FIELD_KEY]
        if (existing != null) {
            val matches = existing.value.equals(correctedSerial, ignoreCase = true)
            val mergedNotes = (baseNotes + existing.notes).toMutableSet()
            val mergedConfidence = if (matches) {
                mergedNotes += FieldNote.VERIFIED_BY_MULTIPLE_SOURCES
                VERIFIED_CONFIDENCE
            } else {
                mergedNotes += FieldNote.CONFLICTING_SOURCES
                CONFLICT_CONFIDENCE
            }
            val verified = FieldConfidence(
                value = correctedSerial,
                confidence = mergedConfidence,
                source = FieldSource.QR,
                notes = mergedNotes
            )
            current[SERIAL_FIELD_KEY] = verified
            ScanAnalytics.reportSerialExtraction(
                SerialExtractionEvent(
                    source = FieldSource.QR,
                    serialPreview = correctedSerial.take(12),
                    confidence = verified.confidence,
                    notes = verified.notes,
                    matchedWithQr = matches
                )
            )
            ScanAnalytics.reportSerialVerification(
                SerialVerificationEvent(
                    ocrSerial = existing.value,
                    qrSerial = correctedSerial,
                    agreed = matches
                )
            )
            return verified
        }

        val qrOnly = FieldConfidence(
            value = correctedSerial,
            confidence = qrConfidenceValue,
            source = FieldSource.QR,
            notes = baseNotes
        )
        current[SERIAL_FIELD_KEY] = qrOnly
        ScanAnalytics.reportSerialExtraction(
            SerialExtractionEvent(
                source = FieldSource.QR,
                serialPreview = correctedSerial.take(12),
                confidence = qrOnly.confidence,
                notes = qrOnly.notes,
                matchedWithQr = null
            )
        )
        return qrOnly
    }

    fun prettifyKey(key: String): String {
        return when (key) {
            SERIAL_FIELD_KEY -> "SIRIM Serial No."
            "batchNo" -> "Batch No."
            "brandTrademark" -> "Brand/Trademark"
            "model" -> "Model"
            "type" -> "Type"
            "rating" -> "Rating"
            "size" -> "Size"
            else -> key.replace(Regex("([A-Z])")) { " ${it.value}" }
                .trim()
                .replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
                }
        }
    }

    fun isValidSerialFormat(serial: String): Boolean {
        if (serial.isBlank()) return false
        val value = serial.trim().uppercase(Locale.ROOT)
        return SIRIM_SERIAL_EXACT.matches(value) || TEA_SERIAL_PATTERN.matches(value)
    }

    private fun extractSerial(text: String): FieldConfidence? {
        SIRIM_SERIAL_EXACT.find(text)?.let { match ->
            val raw = match.value
            val (corrected, notes) = correctCharacters(raw, uppercase = true, enableCorrections = true)
            val penalty = if (notes.isNotEmpty()) CORRECTION_PENALTY else 0f
            val confidence = (EXACT_SERIAL_CONFIDENCE - penalty).coerceIn(MIN_CONFIDENCE, VERIFIED_CONFIDENCE)
            val candidate = FieldConfidence(
                value = corrected,
                confidence = confidence,
                source = FieldSource.OCR,
                notes = notes
            )
            ScanAnalytics.reportSerialExtraction(
                SerialExtractionEvent(
                    source = FieldSource.OCR,
                    serialPreview = corrected.take(12),
                    confidence = candidate.confidence,
                    notes = candidate.notes,
                    matchedWithQr = null
                )
            )
            return candidate
        }

        SIRIM_SERIAL_RELAXED.find(text)?.let { match ->
            val digits = match.groupValues.getOrNull(1)?.filter(Char::isDigit)
            if (digits.isNullOrEmpty() || digits.length != 9) return@let
            val candidate = "T$digits"
            val (corrected, notes) = correctCharacters(candidate, uppercase = true, enableCorrections = true)
            val penalty = if (notes.isNotEmpty()) CORRECTION_PENALTY else 0f
            val confidence = (RELAXED_SERIAL_CONFIDENCE - penalty).coerceIn(MIN_CONFIDENCE, VERIFIED_CONFIDENCE)
            val noteSet = notes + FieldNote.PATTERN_RELAXED
            val field = FieldConfidence(
                value = corrected,
                confidence = confidence,
                source = FieldSource.OCR,
                notes = noteSet
            )
            ScanAnalytics.reportSerialExtraction(
                SerialExtractionEvent(
                    source = FieldSource.OCR,
                    serialPreview = corrected.take(12),
                    confidence = field.confidence,
                    notes = field.notes,
                    matchedWithQr = null
                )
            )
            return field
        }

        TEA_SERIAL_PATTERN.find(text)?.let { match ->
            val digits = match.groupValues.getOrNull(1)?.filter(Char::isDigit)
            if (digits.isNullOrEmpty() || digits.length != 7) return@let
            val candidate = "TEA$digits"
            val (corrected, notes) = correctCharacters(candidate, uppercase = true, enableCorrections = true)
            val penalty = if (notes.isNotEmpty()) CORRECTION_PENALTY else 0f
            val confidence = (LEGACY_SERIAL_CONFIDENCE - penalty).coerceIn(MIN_CONFIDENCE, VERIFIED_CONFIDENCE)
            val noteSet = notes + FieldNote.PATTERN_RELAXED
            val field = FieldConfidence(
                value = corrected,
                confidence = confidence,
                source = FieldSource.OCR,
                notes = noteSet
            )
            ScanAnalytics.reportSerialExtraction(
                SerialExtractionEvent(
                    source = FieldSource.OCR,
                    serialPreview = corrected.take(12),
                    confidence = field.confidence,
                    notes = field.notes,
                    matchedWithQr = null
                )
            )
            return field
        }
        return null
    }

    private fun buildCandidate(
        field: String,
        rawValue: String,
        spec: FieldPatternSpec
    ): FieldConfidence {
        val (corrected, correctionNotes) = correctCharacters(
            value = rawValue,
            uppercase = spec.uppercaseResult,
            enableCorrections = spec.allowCorrections
        )
        val (boundedValue, wasTrimmed) = enforceLength(field, corrected)

        val penalty = buildPenalty(correctionNotes, wasTrimmed)
        val confidence = (spec.baseConfidence - penalty).coerceIn(MIN_CONFIDENCE, MAX_FIELD_CONFIDENCE)

        val notes = (correctionNotes + spec.defaultNotes).toMutableSet()
        if (wasTrimmed) {
            notes += FieldNote.LENGTH_TRIMMED
        }

        return FieldConfidence(
            value = boundedValue,
            confidence = confidence,
            source = FieldSource.OCR,
            notes = notes
        )
    }

    private fun buildPenalty(correctionNotes: Set<FieldNote>, wasTrimmed: Boolean): Float {
        var penalty = 0f
        if (correctionNotes.isNotEmpty()) {
            penalty += CORRECTION_PENALTY
        }
        if (wasTrimmed) {
            penalty += LENGTH_PENALTY
        }
        return penalty
    }

    private fun enforceLength(field: String, value: String): Pair<String, Boolean> {
        val limit = when (field) {
            SERIAL_FIELD_KEY -> 12
            "batchNo" -> 200
            "brandTrademark" -> 1024
            "model", "type", "size" -> 1500
            "rating" -> 600
            else -> 512
        }
        return if (value.length > limit) {
            value.substring(0, min(limit, value.length)) to true
        } else {
            value to false
        }
    }

    private fun correctCharacters(
        value: String,
        uppercase: Boolean,
        enableCorrections: Boolean
    ): Pair<String, Set<FieldNote>> {
        if (value.isEmpty()) {
            val adjusted = if (uppercase) value.uppercase(Locale.ROOT) else value
            return adjusted to emptySet()
        }

        if (!enableCorrections) {
            val adjusted = if (uppercase) value.uppercase(Locale.ROOT) else value
            return adjusted to emptySet()
        }

        val builder = StringBuilder(value.length)
        var correctionsApplied = 0
        value.forEach { char ->
            val replacement = characterCorrections[char] ?: char
            if (replacement != char) {
                correctionsApplied++
            }
            builder.append(replacement)
        }

        val corrected = if (uppercase) builder.toString().uppercase(Locale.ROOT) else builder.toString()
        val notes = if (correctionsApplied > 0) setOf(FieldNote.CORRECTED_CHARACTER) else emptySet()
        return corrected to notes
    }

    private fun normalizeText(text: String): String {
        return text
            .replace('\uFF1A', ':')
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
