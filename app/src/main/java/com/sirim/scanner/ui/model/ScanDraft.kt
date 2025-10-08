package com.sirim.scanner.ui.model

import java.io.Serializable

/**
 * Bundles the outcome of a scanner capture so that the record form can present
 * contextual information such as auto-filled values and capture confidence.
 */
data class ScanDraft(
    val recordId: Long,
    val fieldValues: Map<String, String>,
    val fieldConfidences: Map<String, Float>,
    val captureConfidence: Float,
    val imagePath: String?
) : Serializable {
    val serial: String? = fieldValues["sirimSerialNo"]
}

/**
 * Payload forwarded to the feedback screen when the user reports a scanning
 * issue from the record form.
 */
data class ScanIssueReport(
    val recordId: Long?,
    val serial: String?,
    val captureConfidence: Float?,
    val fieldValues: Map<String, String>,
    val imagePath: String?
) : Serializable
