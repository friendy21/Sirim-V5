package com.sirim.scanner.ui.screens.sku

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sirim.scanner.data.ocr.BarcodeAnalyzer
import com.sirim.scanner.data.repository.SirimRepository
import com.sirim.scanner.data.export.ExportManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkuScannerScreen(
    onBack: () -> Unit,
    onRecordSaved: (Long) -> Unit,
    repository: SirimRepository,
    analyzer: BarcodeAnalyzer,
    appScope: CoroutineScope,
    exportManager: ExportManager
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val viewModel: SkuScannerViewModel = viewModel(
        factory = SkuScannerViewModel.Factory(repository, analyzer, appScope, exportManager)
    )

    val captureState by viewModel.captureState.collectAsState()
    val lastDetection by viewModel.lastDetection.collectAsState()
    val databaseInfo by viewModel.databaseInfo.collectAsState()
    val captureAction = remember { mutableStateOf<(() -> Unit)?>(null) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    val activity = remember(context) { context.findActivity() }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val shouldShowRationale = !hasCameraPermission && activity?.let {
        ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
    } == true
    val showSettingsButton = !hasCameraPermission && !shouldShowRationale && permissionRequested

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission && !permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
            if (hasCameraPermission) {
                SkuCameraPreview(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    lifecycleOwner = lifecycleOwner,
                    viewModel = viewModel,
                    captureState = captureState,
                    captureAction = captureAction
                )
            } else {
                CameraPermissionCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    title = "Camera access required",
                    description = if (shouldShowRationale) {
                        "We need camera access to scan barcodes. Please grant the permission."
                    } else {
                        "Camera permission is required to scan barcodes. You can grant it to continue."
                    },
                    showSettingsButton = showSettingsButton,
                    onRequestPermission = {
                        permissionRequested = true
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    onOpenSettings = { openAppSettings(context) },
                    onCheckPermission = {
                        hasCameraPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    }
                )
            }

            // Capture Button
            Button(
                onClick = { captureAction.value?.invoke() },
                enabled = captureState is CaptureState.Ready && captureAction.value != null,
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
                        is CaptureState.Processing -> "Saving..."
                        is CaptureState.Saved -> "Saved!"
                        is CaptureState.Error -> "Try Again"
                        is CaptureState.Captured -> "Review above"
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
                is CaptureState.Captured -> MaterialTheme.colorScheme.secondaryContainer
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
                        is CaptureState.Captured -> Icons.Rounded.PhotoCamera
                        else -> Icons.Rounded.QrCodeScanner
                    },
                    contentDescription = null,
                    tint = when (state) {
                        is CaptureState.Saved -> MaterialTheme.colorScheme.primary
                        is CaptureState.Error -> MaterialTheme.colorScheme.error
                        is CaptureState.Captured -> MaterialTheme.colorScheme.secondary
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
private fun CameraPermissionCard(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    showSettingsButton: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onCheckPermission: () -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
                Text("Grant permission")
            }
            if (showSettingsButton) {
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open app settings")
                }
                TextButton(onClick = onCheckPermission) {
                    Text("I've granted permission")
                }
            }
        }
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
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
    captureState: CaptureState,
    captureAction: MutableState<(() -> Unit)?>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val camera = remember { mutableStateOf<Camera?>(null) }
    val flashEnabled = rememberSaveable { mutableStateOf(false) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    DisposableEffect(lifecycleOwner) {
        val executor = ContextCompat.getMainExecutor(context)
        val listener = Runnable {
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
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
                analysis,
                imageCapture
            )
            camera.value = boundCamera
        }
        cameraProviderFuture.addListener(listener, executor)
        onDispose {
            captureAction.value = null
            runCatching { cameraProviderFuture.get().unbindAll() }
        }
    }

    LaunchedEffect(flashEnabled.value) {
        camera.value?.cameraControl?.enableTorch(flashEnabled.value)
    }

    LaunchedEffect(captureState, imageCapture) {
        captureAction.value = if (captureState is CaptureState.Ready) {
            {
                captureAction.value = null
                val executor = ContextCompat.getMainExecutor(context)
                val photoFile = File.createTempFile("sku_capture_", ".jpg", context.cacheDir)
                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                imageCapture.takePicture(
                    outputOptions,
                    executor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            scope.launch(Dispatchers.IO) {
                                val bytes = runCatching { photoFile.readBytes() }.getOrNull()
                                photoFile.delete()
                                if (bytes != null) {
                                    withContext(Dispatchers.Main) {
                                        viewModel.onImageCaptured(bytes)
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        viewModel.onCaptureError("Unable to read captured image")
                                    }
                                }
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            photoFile.delete()
                            viewModel.onCaptureError("Capture failed: ${exception.message}")
                        }
                    }
                )
            }
        } else {
            null
        }
    }

    val capturedState = captureState as? CaptureState.Captured
    val previewBitmap = remember(capturedState) {
        capturedState?.imageBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (capturedState != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = "Captured preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Preview unavailable")
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.retakeCapture() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Retake")
                }
                Button(
                    onClick = { viewModel.confirmCapture() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Use photo")
                }
            }
        } else {
            Box(modifier = Modifier.weight(1f)) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )
                SkuScannerOverlay(state = captureState)

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
            is CaptureState.Captured -> Color(0xFF3F51B5)
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
