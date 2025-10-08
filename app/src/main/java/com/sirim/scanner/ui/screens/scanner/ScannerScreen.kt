package com.sirim.scanner.ui.screens.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.sirim.scanner.data.ocr.FieldConfidence
import com.sirim.scanner.data.ocr.SirimLabelParser
import com.sirim.scanner.ui.model.ScanDraft
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel,
    onBack: () -> Unit,
    onRecordSaved: (ScanDraft) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val status by viewModel.status.collectAsState()
    val fields by viewModel.extractedFields.collectAsState()
    val warnings by viewModel.validationWarnings.collectAsState()
    val errors by viewModel.validationErrors.collectAsState()
    val lastDraft by viewModel.lastSavedDraft.collectAsState()
    val batchUiState by viewModel.batchUiState.collectAsState()
    val ocrDebugInfo by viewModel.ocrDebugInfo.collectAsState()
    val hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val showDuplicateDialog = rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(status.state) {
        if (status.state == ScanState.Duplicate) {
            showDuplicateDialog.value = true
        }
    }

    LaunchedEffect(lastDraft) {
        lastDraft?.let {
            onRecordSaved(it)
            viewModel.clearLastSavedDraft()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SIRIM Scanner") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatusCard(status = status)
            OcrDebugPanel(debugInfo = ocrDebugInfo)
            BatchControls(
                uiState = batchUiState,
                onToggle = viewModel::setBatchMode,
                onSave = viewModel::saveBatch,
                onClear = viewModel::clearBatchQueue
            )
            if (hasCameraPermission) {
                CameraPreview(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    lifecycleOwner = lifecycleOwner,
                    viewModel = viewModel,
                    status = status
                )
            } else {
                Text("Camera permission is required to scan labels.")
            }

            ExtractedFieldsPanel(
                fields = fields,
                warnings = warnings,
                errors = errors
            )
        }
    }

    if (showDuplicateDialog.value) {
        AlertDialog(
            onDismissRequest = { showDuplicateDialog.value = false },
            title = { Text("Duplicate Serial Detected") },
            text = { Text("This serial number already exists in the database. Review before saving again.") },
            confirmButton = {
                TextButton(onClick = { showDuplicateDialog.value = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun BatchControls(
    uiState: BatchUiState,
    onToggle: (Boolean) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("Batch mode", style = MaterialTheme.typography.titleSmall)
                    Text("Queued ${uiState.queued.size}", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = uiState.enabled, onCheckedChange = onToggle)
            }

            if (uiState.queued.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.queued, key = { it.serial + it.capturedAt }) { item ->
                        AssistChip(
                            onClick = {},
                            label = {
                                Text("${item.serial} • ${(item.confidence * 100).roundToInt()}%")
                            }
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onSave,
                    enabled = uiState.enabled && uiState.queued.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) { Text("Save Batch") }
                TextButton(
                    onClick = onClear,
                    enabled = uiState.queued.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) { Text("Clear Queue") }
            }
        }
    }
}

@Composable
private fun StatusCard(status: ScanStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = status.message.ifBlank { "Point the camera at the SIRIM label" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            LinearProgressIndicator(
                progress = { status.confidence.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                color = when {
                    status.confidence >= 0.8f -> MaterialTheme.colorScheme.primary
                    status.confidence >= 0.5f -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.error
                }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Confidence ${(status.confidence * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                Text("Frames ${status.frames}", style = MaterialTheme.typography.bodyMedium)
                Text("State ${status.state.name}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ExtractedFieldsPanel(
    fields: Map<String, FieldConfidence>,
    warnings: Map<String, String>,
    errors: Map<String, String>
) {
    if (fields.isEmpty() && warnings.isEmpty() && errors.isEmpty()) {
        return
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Recognized Fields", style = MaterialTheme.typography.titleMedium)
            fields.forEach { (key, confidence) ->
                FieldRow(
                    label = SirimLabelParser.prettifyKey(key),
                    confidence = confidence,
                    warning = warnings[key],
                    error = errors[key]
                )
            }
            if (warnings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                warnings.forEach { (field, message) ->
                    WarningRow(title = "Warning for ${SirimLabelParser.prettifyKey(field)}", message = message)
                }
            }
            if (errors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                errors.forEach { (field, message) ->
                    WarningRow(title = "Error for ${SirimLabelParser.prettifyKey(field)}", message = message, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun FieldRow(
    label: String,
    confidence: FieldConfidence,
    warning: String?,
    error: String?
) {
    val indicatorColor = when {
        error != null -> MaterialTheme.colorScheme.error
        confidence.confidence >= 0.8f -> MaterialTheme.colorScheme.primary
        confidence.confidence >= 0.5f -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.tertiary
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Canvas(modifier = Modifier.size(12.dp)) {
                drawCircle(color = indicatorColor)
            }
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.weight(1f))
            Text("${(confidence.confidence * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(confidence.value.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium)
        AnimatedVisibility(visible = warning != null || error != null, enter = fadeIn(), exit = fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                warning?.let { Text(it, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.tertiary)) }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error)) }
            }
        }
    }
}

@Composable
private fun WarningRow(title: String, message: String, color: Color = MaterialTheme.colorScheme.tertiary) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.labelMedium.copy(color = color, fontWeight = FontWeight.SemiBold))
        Text(message, style = MaterialTheme.typography.bodySmall.copy(color = color))
    }
}

@Composable
private fun CameraPreview(
    modifier: Modifier = Modifier,
    lifecycleOwner: LifecycleOwner,
    viewModel: ScannerViewModel,
    status: ScanStatus

) {
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val camera = remember { mutableStateOf<Camera?>(null) }
    val flashEnabled = rememberSaveable { mutableStateOf(false) }
    val zoomRatio = rememberSaveable { mutableFloatStateOf(1f) }
    val exposureCompensation = rememberSaveable { mutableFloatStateOf(0f) }

    DisposableEffect(lifecycleOwner) {
        val executor = ContextCompat.getMainExecutor(context)
        val listener = Runnable {
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().apply {
                    setAnalyzer(executor) { image ->
                        viewModel.analyzeImage(image)
                    }
                }
            val boundCamera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )
            camera.value = boundCamera
            enableTapToFocus(previewView, boundCamera.cameraControl, previewView.meteringPointFactory)
        }
        cameraProviderFuture.addListener(listener, executor)

        onDispose {
            runCatching { cameraProviderFuture.get().unbindAll() }
        }
    }

    LaunchedEffect(flashEnabled.value) {
        camera.value?.cameraControl?.enableTorch(flashEnabled.value)
    }

    LaunchedEffect(zoomRatio.floatValue) {
        camera.value?.cameraControl?.setZoomRatio(zoomRatio.floatValue.coerceAtLeast(1f))
    }

    LaunchedEffect(exposureCompensation.floatValue) {
        camera.value?.cameraControl?.setExposureCompensationIndex(exposureCompensation.floatValue.toInt())
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
            ScannerOverlay(status = status)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { flashEnabled.value = !flashEnabled.value }) {
                Icon(
                    imageVector = if (flashEnabled.value) Icons.Rounded.Bolt else Icons.Rounded.FlashOff,
                    contentDescription = if (flashEnabled.value) "Flash on" else "Flash off"
                )
            }
            ZoomSlider(
                value = zoomRatio.floatValue,
                onValueChange = { zoomRatio.floatValue = it },
                range = 1f..4f,
                label = "Zoom",
                steps = 8
            )
        }
        camera.value?.cameraInfo?.exposureState?.let { exposureState ->
            ZoomSlider(
                value = exposureCompensation.floatValue,
                onValueChange = {
                    val rounded = it.roundToInt().toFloat()
                    exposureCompensation.floatValue = rounded.coerceIn(
                        exposureState.exposureCompensationRange.lower.toFloat(),
                        exposureState.exposureCompensationRange.upper.toFloat()
                    )
                },
                range = exposureState.exposureCompensationRange.lower.toFloat()..exposureState.exposureCompensationRange.upper.toFloat(),
                label = "Exposure",
                steps = (exposureState.exposureCompensationRange.upper - exposureState.exposureCompensationRange.lower)
            )
        }
    }
}

private fun enableTapToFocus(
    previewView: PreviewView,
    cameraControl: androidx.camera.core.CameraControl,
    meteringPointFactory: MeteringPointFactory
) {
    previewView.afterMeasured {
        setOnTouchListener { _: View, event: MotionEvent ->
            if (event.action != MotionEvent.ACTION_UP) return@setOnTouchListener true
            val point = meteringPointFactory.createPoint(event.x, event.y)
            val action = FocusMeteringAction.Builder(point)
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build()
            cameraControl.startFocusAndMetering(action)
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            true
        }
    }
}

@Composable
private fun ZoomSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    label: String,
    modifier: Modifier = Modifier,
    steps: Int = 0
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(String.format("%.1f", value), style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps
        )
    }
}

@Composable
private fun ScannerOverlay(status: ScanStatus) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val padding = 48.dp.toPx()
        val width = size.width - padding * 2
        val height = size.height - padding * 2
        drawRoundRect(
            color = when (status.state) {
                ScanState.Ready, ScanState.Persisted -> Color(0xFF4CAF50)
                ScanState.Partial -> Color(0xFFFFC107)
                ScanState.Error, ScanState.Duplicate -> Color(0xFFF44336)
                else -> Color.White.copy(alpha = 0.6f)
            },
            topLeft = androidx.compose.ui.geometry.Offset(padding, padding),
            size = androidx.compose.ui.geometry.Size(width, height),
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

private fun View.afterMeasured(block: View.() -> Unit) {
    if (width > 0 && height > 0) {
        block()
    } else {
        addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View?,
                left: Int,
                top: Int,
                right: Int,
                bottom: Int,
                oldLeft: Int,
                oldTop: Int,
                oldRight: Int,
                oldBottom: Int
            ) {
                if (width > 0 && height > 0) {
                    removeOnLayoutChangeListener(this)
                    block()
                }
            }
        })
    }
}
