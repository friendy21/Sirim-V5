package com.sirim.scanner.ui.screens.sku

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Camera
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkuRecordFormScreen(
    viewModel: SkuRecordFormViewModel,
    recordId: Long?,
    onSaved: (Long) -> Unit,
    onBack: () -> Unit,
    onRetake: (() -> Unit)? = null
) {
    val scrollState = rememberScrollState()

    val barcodeState = remember { mutableStateOf(TextFieldValue()) }
    val batchState = remember { mutableStateOf(TextFieldValue()) }
    val brandState = remember { mutableStateOf(TextFieldValue()) }
    val modelState = remember { mutableStateOf(TextFieldValue()) }
    val typeState = remember { mutableStateOf(TextFieldValue()) }
    val ratingState = remember { mutableStateOf(TextFieldValue()) }
    val sizeState = remember { mutableStateOf(TextFieldValue()) }
    var verified by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(recordId) {
        if (recordId != null) {
            viewModel.loadRecord(recordId)
        } else {
            viewModel.reset()
            barcodeState.value = TextFieldValue("")
            batchState.value = TextFieldValue("")
            brandState.value = TextFieldValue("")
            modelState.value = TextFieldValue("")
            typeState.value = TextFieldValue("")
            ratingState.value = TextFieldValue("")
            sizeState.value = TextFieldValue("")
            verified = false
        }
    }

    val record by viewModel.activeRecord.collectAsState()
    val galleryPaths by viewModel.galleryPaths.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val formError by viewModel.formError.collectAsState()

    LaunchedEffect(record?.id) {
        record?.let { sku ->
            barcodeState.value = TextFieldValue(sku.barcode)
            batchState.value = TextFieldValue(sku.batchNo.orEmpty())
            brandState.value = TextFieldValue(sku.brandTrademark.orEmpty())
            modelState.value = TextFieldValue(sku.model.orEmpty())
            typeState.value = TextFieldValue(sku.type.orEmpty())
            ratingState.value = TextFieldValue(sku.rating.orEmpty())
            sizeState.value = TextFieldValue(sku.size.orEmpty())
            verified = sku.isVerified
        }
    }

    val primaryBitmap = remember(record?.imagePath) {
        record?.imagePath?.takeIf { it.isNotBlank() }?.let { path ->
            BitmapFactory.decodeFile(path)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        uris.forEach { uri ->
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use(InputStream::readBytes)
                }
                if (bytes != null && bytes.isNotEmpty()) {
                    viewModel.addGalleryImage(bytes)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SKU Record") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (onRetake != null) {
                        IconButton(onClick = onRetake) {
                            Icon(Icons.Rounded.Camera, contentDescription = "Retake photo")
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
            if (primaryBitmap != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Image(
                        bitmap = primaryBitmap.asImageBitmap(),
                        contentDescription = "Captured barcode",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            if (isSaving) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            OutlinedTextField(
                value = barcodeState.value,
                onValueChange = { barcodeState.value = it },
                label = { Text("Barcode") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = batchState.value,
                onValueChange = { batchState.value = it },
                label = { Text("Batch No.") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = brandState.value,
                onValueChange = { brandState.value = it },
                label = { Text("Brand/Trademark") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = modelState.value,
                onValueChange = { modelState.value = it },
                label = { Text("Model") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = typeState.value,
                onValueChange = { typeState.value = it },
                label = { Text("Type") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = ratingState.value,
                onValueChange = { ratingState.value = it },
                label = { Text("Rating") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = sizeState.value,
                onValueChange = { sizeState.value = it },
                label = { Text("Size") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Verified", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = verified, onCheckedChange = { verified = it })
            }

            formError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Gallery Images", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }) {
                        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add from gallery")
                    }
                }

                if (galleryPaths.isEmpty()) {
                    Text(
                        "No additional images",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(galleryPaths) { path ->
                            GalleryImageThumbnail(
                                path = path,
                                onRemove = { viewModel.removeGalleryImage(path) }
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    if (isSaving) return@Button
                    viewModel.saveRecord(
                        barcode = barcodeState.value.text,
                        batchNo = batchState.value.text,
                        brand = brandState.value.text,
                        model = modelState.value.text,
                        type = typeState.value.text,
                        rating = ratingState.value.text,
                        size = sizeState.value.text,
                        isVerified = verified,
                        onSaved = onSaved
                    )
                },
                enabled = barcodeState.value.text.isNotBlank() && !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Saving")
                } else {
                    Icon(Icons.Rounded.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Record")
                }
            }
        }
    }
}

@Composable
private fun GalleryImageThumbnail(
    path: String,
    onRemove: () -> Unit
) {
    val bitmap = remember(path) { BitmapFactory.decodeFile(path) }
    Card(
        modifier = Modifier
            .size(120.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Gallery image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Unavailable", style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
