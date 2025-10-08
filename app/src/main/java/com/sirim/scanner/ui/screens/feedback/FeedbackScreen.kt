package com.sirim.scanner.ui.screens.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.sirim.scanner.data.ocr.SirimLabelParser
import com.sirim.scanner.feedback.FeedbackManager
import com.sirim.scanner.feedback.FeedbackSubmission
import com.sirim.scanner.ui.model.ScanIssueReport
import kotlin.text.StringBuilder
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    onBack: () -> Unit,
    prefill: ScanIssueReport? = null
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val defaultDescription = remember(prefill) { prefill?.let(::buildPrefillDescription).orEmpty() }
    var descriptionState by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(defaultDescription))
    }
    var contactState by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    var includeDiagnostics by rememberSaveable { mutableStateOf(prefill?.imagePath != null) }
    var isSubmitting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Send Feedback") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Feedback,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Text(
                text = "Spotted a label that refuses to scan? Tell us what happened so we can improve the recogniser.",
                style = MaterialTheme.typography.bodyMedium
            )
            prefill?.let {
                PrefilledContextCard(report = it)
            }
            OutlinedTextField(
                value = descriptionState,
                onValueChange = { descriptionState = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                label = { Text("What went wrong?") },
                supportingText = { Text("Include the serial, conditions, or anything else that helps us reproduce it.") },
                minLines = 5
            )
            OutlinedTextField(
                value = contactState,
                onValueChange = { contactState = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Contact (optional)") },
                supportingText = { Text("Email or phone so we can follow up if needed.") }
            )
            RowWithCheckbox(
                checked = includeDiagnostics,
                onCheckedChange = { includeDiagnostics = it }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    if (descriptionState.text.isBlank() || isSubmitting) return@Button
                    scope.launch {
                        isSubmitting = true
                        val submission = FeedbackSubmission(
                            description = descriptionState.text.trim(),
                            contact = contactState.text.trim().takeIf { it.isNotEmpty() },
                            includeDiagnostics = includeDiagnostics
                        )
                        val result = runCatching { FeedbackManager.submitFeedback(submission) }
                        isSubmitting = false
                        if (result.isSuccess) {
                            descriptionState = TextFieldValue()
                            contactState = TextFieldValue()
                            includeDiagnostics = true
                            snackbarHostState.showSnackbar(
                                message = "Thanks! Your feedback has been queued.",
                                withDismissAction = true
                            )
                        } else {
                            snackbarHostState.showSnackbar(
                                message = "Unable to send feedback right now.",
                                withDismissAction = true
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = descriptionState.text.isNotBlank() && !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Sending...")
                } else {
                    Text("Submit feedback")
                }
            }
        }
    }

    LaunchedEffect(prefill) {
        if (prefill != null && descriptionState.text.isBlank()) {
            descriptionState = TextFieldValue(defaultDescription)
        }
    }
    LaunchedEffect(Unit) {
        snackbarHostState.currentSnackbarData?.dismiss()
    }
}

@Composable
private fun RowWithCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Column(modifier = Modifier.weight(1f)) {
            Text("Attach diagnostic logs", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "Includes anonymised OCR metrics to help reproduce problems.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PrefilledContextCard(report: ScanIssueReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Attached scan details", style = MaterialTheme.typography.titleSmall)
            report.serial?.let {
                Text("Serial: $it", style = MaterialTheme.typography.bodyMedium)
            }
            report.captureConfidence?.let {
                Text(
                    "Confidence: ${(it * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (report.fieldValues.isNotEmpty()) {
                Text("Captured fields:", style = MaterialTheme.typography.bodySmall)
                report.fieldValues.entries.sortedBy { it.key }.forEach { (key, value) ->
                    val label = SirimLabelParser.prettifyKey(key)
                    Text("• $label: ${value.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                text = if (report.imagePath != null) "Label snapshot included" else "No image available",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun buildPrefillDescription(report: ScanIssueReport): String {
    val builder = StringBuilder()
    report.serial?.let { builder.appendLine("Serial: $it") }
    report.captureConfidence?.let {
        val pct = (it * 100).coerceIn(0f, 100f)
        builder.appendLine("Scanner confidence: ${"%.1f".format(pct)}%")
    }
    if (report.fieldValues.isNotEmpty()) {
        builder.appendLine("Captured values:")
        report.fieldValues.forEach { (key, value) ->
            val label = SirimLabelParser.prettifyKey(key)
            builder.appendLine("- $label: ${value.ifBlank { "(blank)" }}")
        }
    }
    builder.appendLine()
    builder.append("Describe what went wrong: ")
    return builder.toString()
}

