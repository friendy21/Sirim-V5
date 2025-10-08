package com.sirim.scanner.ui.screens.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.sirim.scanner.data.db.SirimRecord
import com.sirim.scanner.ui.model.ScanDraft
import com.sirim.scanner.ui.model.ScanIssueReport
import java.util.LinkedHashMap
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordFormScreen(
    viewModel: RecordViewModel,
    onSaved: (Long) -> Unit,
    onBack: () -> Unit,
    onRetake: (() -> Unit)? = null,
    onReportIssue: ((ScanIssueReport) -> Unit)? = null,
    recordId: Long? = null,
    scanDraft: ScanDraft? = null
) {
    val scrollState = rememberScrollState()

    val serialState = remember { mutableStateOf(TextFieldValue()) }
    val batchState = remember { mutableStateOf(TextFieldValue()) }
    val brandState = remember { mutableStateOf(TextFieldValue()) }
    val modelState = remember { mutableStateOf(TextFieldValue()) }
    val typeState = remember { mutableStateOf(TextFieldValue()) }
    val ratingState = remember { mutableStateOf(TextFieldValue()) }
    val sizeState = remember { mutableStateOf(TextFieldValue()) }

    val customFields = remember { mutableStateListOf<CustomFieldUiState>() }
    var fieldIdCounter by rememberSaveable { mutableStateOf(0L) }
    var showAddFieldDialog by rememberSaveable { mutableStateOf(false) }

    val fieldConfidences = scanDraft?.fieldConfidences ?: emptyMap()

    fun resetStates() {
        serialState.value = TextFieldValue("")
        batchState.value = TextFieldValue("")
        brandState.value = TextFieldValue("")
        modelState.value = TextFieldValue("")
        typeState.value = TextFieldValue("")
        ratingState.value = TextFieldValue("")
        sizeState.value = TextFieldValue("")
        customFields.clear()
    }

    LaunchedEffect(recordId) {
        recordId?.let { id ->
            viewModel.loadRecord(id)
            scanDraft?.let { draft ->
                applyDraftToStates(
                    draft = draft,
                    serialState = serialState,
                    batchState = batchState,
                    brandState = brandState,
                    modelState = modelState,
                    typeState = typeState,
                    ratingState = ratingState,
                    sizeState = sizeState
                )
            }
        } ?: run {
            viewModel.resetActiveRecord()
            viewModel.clearFormError()
            resetStates()
            scanDraft?.let { draft ->
                applyDraftToStates(
                    draft = draft,
                    serialState = serialState,
                    batchState = batchState,
                    brandState = brandState,
                    modelState = modelState,
                    typeState = typeState,
                    ratingState = ratingState,
                    sizeState = sizeState
                )
            }
        }
    }

    val activeRecord by viewModel.activeRecord.collectAsState()
    val formError by viewModel.formError.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()

    LaunchedEffect(scanDraft?.recordId, activeRecord?.id) {
        if (recordId != null && scanDraft != null && activeRecord == null) {
            applyDraftToStates(
                draft = scanDraft,
                serialState = serialState,
                batchState = batchState,
                brandState = brandState,
                modelState = modelState,
                typeState = typeState,
                ratingState = ratingState,
                sizeState = sizeState
            )
        }
    }

    LaunchedEffect(activeRecord?.id) {
        activeRecord?.let { record ->
            serialState.value = TextFieldValue(record.sirimSerialNo)
            batchState.value = TextFieldValue(record.batchNo)
            brandState.value = TextFieldValue(record.brandTrademark)
            modelState.value = TextFieldValue(record.model)
            typeState.value = TextFieldValue(record.type)
            ratingState.value = TextFieldValue(record.rating)
            sizeState.value = TextFieldValue(record.size)
            customFields.clear()
            val entries = record.customFields.toCustomFieldEntries()
            entries.forEach { entry ->
                fieldIdCounter += 1
                customFields += CustomFieldUiState(
                    id = fieldIdCounter,
                    name = entry.name,
                    maxLength = entry.maxLength,
                    value = TextFieldValue(entry.value)
                )
            }
            viewModel.clearFormError()
        }
    }

    val captureConfidence = scanDraft?.captureConfidence ?: activeRecord?.captureConfidence
    val imagePath = activeRecord?.imagePath ?: scanDraft?.imagePath

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Record Form") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (onRetake != null) {
                        IconButton(onClick = onRetake) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Retake scan")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if ((scanDraft != null || activeRecord?.imagePath != null)) {
                AutoFillIndicator(
                    captureConfidence = captureConfidence,
                    onRetake = onRetake,
                    onReportIssue = onReportIssue?.let {
                        {
                            it(
                                buildScanIssueReport(
                                    record = activeRecord,
                                    scanDraft = scanDraft,
                                    serial = serialState.value.text,
                                    batch = batchState.value.text,
                                    brand = brandState.value.text,
                                    model = modelState.value.text,
                                    type = typeState.value.text,
                                    rating = ratingState.value.text,
                                    size = sizeState.value.text,
                                    customFields = customFields,
                                    captureConfidence = captureConfidence,
                                    imagePath = imagePath
                                )
                            )
                        }
                    }
                )
            }

            if (isSaving) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            FieldInput(
                value = serialState.value,
                onValueChange = {
                    serialState.value = it
                    viewModel.clearFormError()
                },
                label = "SIRIM Serial No. *",
                supportingText = {
                    Text("T + 9 digits, no spaces", style = MaterialTheme.typography.bodySmall)
                },
                isError = formError != null,
                trailingIcon = if (formError != null) {
                    {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    null
                },
                confidence = fieldConfidences["sirimSerialNo"]
            )
            formError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            FieldInput(
                value = batchState.value,
                onValueChange = { batchState.value = it },
                label = "Batch No.",
                supportingText = { Text("Max 200 characters") },
                confidence = fieldConfidences["batchNo"]
            )
            FieldInput(
                value = brandState.value,
                onValueChange = { brandState.value = it },
                label = "Brand/Trademark",
                supportingText = { Text("Max 1024 characters") },
                confidence = fieldConfidences["brandTrademark"]
            )
            FieldInput(
                value = modelState.value,
                onValueChange = { modelState.value = it },
                label = "Model",
                supportingText = { Text("Max 1500 characters") },
                confidence = fieldConfidences["model"]
            )
            FieldInput(
                value = typeState.value,
                onValueChange = { typeState.value = it },
                label = "Type",
                supportingText = { Text("Max 1500 characters") },
                confidence = fieldConfidences["type"]
            )
            FieldInput(
                value = ratingState.value,
                onValueChange = { ratingState.value = it },
                label = "Rating",
                supportingText = { Text("Max 600 characters") },
                confidence = fieldConfidences["rating"]
            )
            FieldInput(
                value = sizeState.value,
                onValueChange = { sizeState.value = it },
                label = "Size",
                supportingText = { Text("Max 1500 characters") },
                confidence = fieldConfidences["size"]
            )

            if (customFields.isNotEmpty()) {
                Text("Additional Fields", style = MaterialTheme.typography.titleMedium)
                customFields.forEachIndexed { index, field ->
                    CustomFieldRow(
                        field = field,
                        onValueChange = { newValue ->
                            if (newValue.text.length <= field.maxLength) {
                                customFields[index] = field.copy(value = newValue)
                            } else {
                                customFields[index] = field.copy(
                                    value = TextFieldValue(newValue.text.take(field.maxLength))
                                )
                            }
                        },
                        onDelete = { customFields.removeAt(index) }
                    )
                }
            }

            OutlinedButton(
                onClick = { showAddFieldDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Custom Field")
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        if (isSaving) return@Button
                        val serial = serialState.value.text.trim()
                        val batch = batchState.value.text.trim()
                        val brand = brandState.value.text.trim()
                        val model = modelState.value.text.trim()
                        val type = typeState.value.text.trim()
                        val rating = ratingState.value.text.trim()
                        val size = sizeState.value.text.trim()

                        val customEntries = customFields.mapNotNull { it.toEntry() }
                        val customJson = customEntries.takeIf { it.isNotEmpty() }?.toJson()

                        val record = activeRecord?.copy(
                            sirimSerialNo = serial,
                            batchNo = batch,
                            brandTrademark = brand,
                            model = model,
                            type = type,
                            rating = rating,
                            size = size,
                            customFields = customJson,
                            captureConfidence = captureConfidence,
                            imagePath = activeRecord?.imagePath ?: imagePath
                        ) ?: SirimRecord(
                            sirimSerialNo = serial,
                            batchNo = batch,
                            brandTrademark = brand,
                            model = model,
                            type = type,
                            rating = rating,
                            size = size,
                            imagePath = imagePath,
                            customFields = customJson,
                            captureConfidence = captureConfidence
                        )

                        viewModel.createOrUpdate(record) { id ->
                            onSaved(id)
                        }
                    },
                    enabled = !isSaving && serialState.value.text.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Saving...")
                    } else {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save Record")
                    }
                }

                if (onReportIssue != null) {
                    OutlinedButton(
                        onClick = {
                            onReportIssue(
                                buildScanIssueReport(
                                    record = activeRecord,
                                    scanDraft = scanDraft,
                                    serial = serialState.value.text,
                                    batch = batchState.value.text,
                                    brand = brandState.value.text,
                                    model = modelState.value.text,
                                    type = typeState.value.text,
                                    rating = ratingState.value.text,
                                    size = sizeState.value.text,
                                    customFields = customFields,
                                    captureConfidence = captureConfidence,
                                    imagePath = imagePath
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Feedback, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Report scanning issue")
                    }
                }
            }
        }
    }

    if (showAddFieldDialog) {
        AddCustomFieldDialog(
            onDismiss = { showAddFieldDialog = false },
            onAdd = { name, maxLength ->
                if (name.isNotBlank()) {
                    fieldIdCounter += 1
                    customFields += CustomFieldUiState(
                        id = fieldIdCounter,
                        name = name.trim(),
                        maxLength = maxLength,
                        value = TextFieldValue("")
                    )
                }
                showAddFieldDialog = false
            }
        )
    }
}

@Composable
private fun FieldInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    supportingText: (@Composable (() -> Unit))? = null,
    isError: Boolean = false,
    trailingIcon: (@Composable (() -> Unit))? = null,
    confidence: Float?
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            supportingText = supportingText,
            isError = isError,
            trailingIcon = trailingIcon,
            modifier = Modifier.fillMaxWidth()
        )
        FieldConfidenceIndicator(confidence)
    }
}

@Composable
private fun FieldConfidenceIndicator(confidence: Float?, modifier: Modifier = Modifier) {
    confidence ?: return
    val normalized = confidence.coerceIn(0f, 1f)
    val percentage = (normalized * 100).roundToInt()
    val (icon, tint, label) = when {
        normalized >= 0.9f -> Triple(
            Icons.Default.CheckCircle,
            MaterialTheme.colorScheme.tertiary,
            "High confidence"
        )
        normalized >= 0.7f -> Triple(
            Icons.Default.Info,
            MaterialTheme.colorScheme.secondary,
            "Moderate confidence"
        )
        else -> Triple(
            Icons.Default.Warning,
            MaterialTheme.colorScheme.error,
            "Low confidence"
        )
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$label • ${percentage}%",
                style = MaterialTheme.typography.bodySmall,
                color = tint
            )
        }
        LinearProgressIndicator(
            progress = normalized,
            modifier = Modifier.fillMaxWidth(),
            color = tint,
            trackColor = tint.copy(alpha = 0.2f)
        )
    }
}

@Composable
private fun AutoFillIndicator(
    captureConfidence: Float?,
    onRetake: (() -> Unit)?,
    onReportIssue: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Fields auto-filled from scan. Review and edit as needed.",
                style = MaterialTheme.typography.bodyMedium
            )
            captureConfidence?.let {
                val percentage = (it.coerceIn(0f, 1f) * 100).roundToInt()
                LinearProgressIndicator(
                    progress = it.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                )
                Text(
                    "Capture confidence: $percentage%",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onRetake != null) {
                    TextButton(onClick = onRetake) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retake")
                    }
                }
                if (onReportIssue != null) {
                    TextButton(onClick = onReportIssue) {
                        Icon(Icons.Default.Feedback, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Report issue")
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomFieldRow(
    field: CustomFieldUiState,
    onValueChange: (TextFieldValue) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        OutlinedTextField(
            value = field.value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            label = { Text(field.name) },
            supportingText = { Text("Max ${field.maxLength} characters") }
        )
        IconButton(onClick = onDelete, modifier = Modifier.padding(top = 8.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Remove field")
        }
    }
}

@Composable
private fun AddCustomFieldDialog(
    onDismiss: () -> Unit,
    onAdd: (String, Int) -> Unit
) {
    var fieldName by remember { mutableStateOf(TextFieldValue()) }
    var maxLength by remember { mutableStateOf(TextFieldValue("500")) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Custom Field") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = fieldName,
                    onValueChange = { fieldName = it },
                    label = { Text("Field name") },
                    placeholder = { Text("e.g. Manufacturer") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = maxLength,
                    onValueChange = {
                        val digits = it.text.filter(Char::isDigit)
                        maxLength = TextFieldValue(digits)
                    },
                    label = { Text("Max length") },
                    placeholder = { Text("500") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val length = maxLength.text.toIntOrNull()?.coerceAtLeast(1) ?: 500
                    onAdd(fieldName.text, length)
                },
                enabled = fieldName.text.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private data class CustomFieldUiState(
    val id: Long,
    val name: String,
    val maxLength: Int,
    val value: TextFieldValue
) {
    fun toEntry(): CustomFieldEntry? {
        val trimmedValue = value.text.trim()
        if (name.isBlank() && trimmedValue.isBlank()) return null
        return CustomFieldEntry(
            name = name,
            value = trimmedValue.take(maxLength),
            maxLength = maxLength
        )
    }
}

private fun applyDraftToStates(
    draft: ScanDraft,
    serialState: MutableState<TextFieldValue>,
    batchState: MutableState<TextFieldValue>,
    brandState: MutableState<TextFieldValue>,
    modelState: MutableState<TextFieldValue>,
    typeState: MutableState<TextFieldValue>,
    ratingState: MutableState<TextFieldValue>,
    sizeState: MutableState<TextFieldValue>
) {
    val values = draft.fieldValues
    serialState.value = TextFieldValue(values["sirimSerialNo"].orEmpty())
    batchState.value = TextFieldValue(values["batchNo"].orEmpty())
    brandState.value = TextFieldValue(values["brandTrademark"].orEmpty())
    modelState.value = TextFieldValue(values["model"].orEmpty())
    typeState.value = TextFieldValue(values["type"].orEmpty())
    ratingState.value = TextFieldValue(values["rating"].orEmpty())
    sizeState.value = TextFieldValue(values["size"].orEmpty())
}

private fun buildScanIssueReport(
    record: SirimRecord?,
    scanDraft: ScanDraft?,
    serial: String,
    batch: String,
    brand: String,
    model: String,
    type: String,
    rating: String,
    size: String,
    customFields: List<CustomFieldUiState>,
    captureConfidence: Float?,
    imagePath: String?
): ScanIssueReport {
    val values = LinkedHashMap<String, String>()
    values["sirimSerialNo"] = serial
    values["batchNo"] = batch
    values["brandTrademark"] = brand
    values["model"] = model
    values["type"] = type
    values["rating"] = rating
    values["size"] = size
    customFields.forEach { field ->
        values[field.name] = field.value.text
    }
    return ScanIssueReport(
        recordId = record?.id ?: scanDraft?.recordId,
        serial = serial.ifBlank { scanDraft?.serial ?: record?.sirimSerialNo },
        captureConfidence = captureConfidence,
        fieldValues = values,
        imagePath = imagePath
    )
}