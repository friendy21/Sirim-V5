package com.sirim.scanner.ui.screens.sku

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sirim.scanner.data.ocr.BarcodeAnalyzer
import com.sirim.scanner.data.repository.SirimRepository
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkuScannerScreen(
    onBack: () -> Unit,
    onRecordSaved: (Long) -> Unit,
    repository: SirimRepository,
    analyzer: BarcodeAnalyzer,
    appScope: CoroutineScope
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val viewModel: SkuScannerViewModel = viewModel(
        factory = SkuScannerViewModel.Factory(repository, analyzer, appScope)
    )

    val captureState by viewModel.captureState.collectAsState()
    val lastDetection by viewModel.lastDetection.collectAsState()
    val databaseInfo by viewModel.databaseInfo.collectAsState()

    val hasCameraPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    LaunchedEffect(captureState) {
        if (captureState is CaptureState.Saved) {
            (captureState as CaptureState.Saved).recordId?.let(onRecordSaved)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SKU Barcode Scanner") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
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
            // Status Card
            SkuStatusCard(state = captureState, detection = lastDetection)

            // Database Info Card
            if (databaseInfo != null) {
                DatabaseInfoCard(info = databaseInfo!!)
            }

            // Camera Preview
            if (hasCameraPermission.value) {
                SkuCameraPreview(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    lifecycleOwner = lifecycleOwner,
                    viewModel = viewModel,
                    captureState = captureState
                )
            } else {
                Text(
                    text = "Camera permission is required to scan barcodes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Capture Button
            Button(
                onClick = { viewModel.captureBarcode() },
                enabled = captureState is CaptureState.Ready,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(
                    Icons.Rounded.Camera,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    when (captureState) {
                        is CaptureState.Processing -> "Processing..."
                        is CaptureState.Saved -> "Saved!"
                        is CaptureState.Error -> "Try Again"
                        else -> "Capture Barcode"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun SkuStatusCard(
    state: CaptureState,
    detection: BarcodeDetectionInfo?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (state) {
                is CaptureState.Saved -> MaterialTheme.colorScheme.primaryContainer
                is CaptureState.Error -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    when (state) {
                        is CaptureState.Saved -> Icons.Rounded.CheckCircle
                        is CaptureState.Error -> Icons.Rounded.Error
                        is CaptureState.Processing -> Icons.Rounded.HourglassEmpty
                        else -> Icons.Rounded.QrCodeScanner
                    },
                    contentDescription = null,
                    tint = when (state) {
                        is CaptureState.Saved -> MaterialTheme.colorScheme.primary
                        is CaptureState.Error -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(32.dp)
                )
                Column {
                    Text(
                        state.message,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (state is CaptureState.Saved && state.isNewRecord) {
                        Text(
                            "New database created",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (state is CaptureState.Saved && !state.isNewRecord) {
                        Text(
                            "Using existing database",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            if (detection != null) {
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Detected Barcode:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        detection.value,
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Format: ${detection.format}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DatabaseInfoCard(info: SkuDatabaseInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Database Information",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Total Barcodes", style = MaterialTheme.typography.labelMedium)
                    Text(
                        info.totalCount.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Unique Codes", style = MaterialTheme.typography.labelMedium)
                    Text(
                        info.uniqueCount.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SkuCameraPreview(
    modifier: Modifier,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    viewModel: SkuScannerViewModel,
    captureState: CaptureState
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

    DisposableEffect(lifecycleOwner) {
        val executor = ContextCompat.getMainExecutor(context)
        val listener = Runnable {
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .apply {
                    setAnalyzer(executor) { image ->
                        viewModel.analyzeFrame(image)
                    }
                }
            val boundCamera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )
            camera.value = boundCamera
        }
        cameraProviderFuture.addListener(listener, executor)
        onDispose {
            runCatching { cameraProviderFuture.get().unbindAll() }
        }
    }

    LaunchedEffect(flashEnabled.value) {
        camera.value?.cameraControl?.enableTorch(flashEnabled.value)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
            SkuScannerOverlay(state = captureState)

            // Flash Toggle
            IconButton(
                onClick = { flashEnabled.value = !flashEnabled.value },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = if (flashEnabled.value) Icons.Rounded.Bolt else Icons.Rounded.FlashOff,
                    contentDescription = if (flashEnabled.value) "Disable flash" else "Enable flash",
                    tint = if (flashEnabled.value) Color.Yellow else Color.White
                )
            }
        }
    }
}

@Composable
private fun SkuScannerOverlay(state: CaptureState) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val padding = 80.dp.toPx()
        val width = size.width - padding * 2
        val height = (size.height * 0.4f).coerceAtMost(width * 0.6f)
        val top = (size.height - height) / 2

        val color = when (state) {
            is CaptureState.Saved -> Color(0xFF4CAF50)
            is CaptureState.Error -> Color(0xFFF44336)
            is CaptureState.Processing -> Color(0xFFFFC107)
            is CaptureState.Ready -> Color(0xFF2196F3)
            else -> Color.White.copy(alpha = 0.7f)
        }

        drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(padding, top),
            size = androidx.compose.ui.geometry.Size(width, height),
            style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx())
        )
    }
}
