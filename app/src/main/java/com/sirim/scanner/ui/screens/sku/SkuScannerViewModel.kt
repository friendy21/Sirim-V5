package com.sirim.scanner.ui.screens.sku

import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sirim.scanner.data.db.SkuRecord
import com.sirim.scanner.data.ocr.BarcodeAnalyzer
import com.sirim.scanner.data.ocr.BarcodeDetection
import com.sirim.scanner.data.repository.SirimRepository
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SkuScannerViewModel private constructor(
    private val repository: SirimRepository,
    private val analyzer: BarcodeAnalyzer,
    private val appScope: CoroutineScope
) : ViewModel() {

    private val processing = AtomicBoolean(false)

    private val _captureState = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val captureState: StateFlow<CaptureState> = _captureState.asStateFlow()

    private val _lastDetection = MutableStateFlow<BarcodeDetectionInfo?>(null)
    val lastDetection: StateFlow<BarcodeDetectionInfo?> = _lastDetection.asStateFlow()

    private val _databaseInfo = MutableStateFlow<SkuDatabaseInfo?>(null)
    val databaseInfo: StateFlow<SkuDatabaseInfo?> = _databaseInfo.asStateFlow()

    private var pendingDetection: BarcodeDetection? = null

    init {
        loadDatabaseInfo()
    }

    fun analyzeFrame(imageProxy: ImageProxy) {
        if (!processing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        viewModelScope.launch {
            try {
                val detection = analyzer.analyze(imageProxy)
                if (detection != null && detection.value.isNotBlank()) {
                    pendingDetection = detection
                    _lastDetection.value = BarcodeDetectionInfo(
                        value = detection.value,
                        format = detection.format
                    )
                    _captureState.value = CaptureState.Ready("Barcode detected - Tap capture to save")
                } else {
                    if (_captureState.value !is CaptureState.Processing && 
                        _captureState.value !is CaptureState.Saved) {
                        _captureState.value = CaptureState.Idle
                    }
                }
            } catch (error: Exception) {
                _captureState.value = CaptureState.Error("Failed to analyze frame: ${error.message}")
            } finally {
                imageProxy.close()
                processing.set(false)
            }
        }
    }

    fun captureBarcode() {
        val detection = pendingDetection ?: run {
            _captureState.value = CaptureState.Error("No barcode detected")
            return
        }

        appScope.launch {
            _captureState.value = CaptureState.Processing("Saving barcode...")

            try {
                val normalized = detection.value.trim()
                if (normalized.isEmpty()) {
                    _captureState.value = CaptureState.Error("Barcode value is empty")
                    return@launch
                }

                // Check if barcode already exists
                val existing = repository.findByBarcode(normalized)
                
                val (recordId, isNew) = if (existing != null) {
                    // Use existing database
                    existing.id to false
                } else {
                    // Create new database
                    val record = SkuRecord(
                        barcode = normalized,
                        createdAt = System.currentTimeMillis()
                    )
                    val id = repository.upsertSku(record)
                    id to true
                }

                _captureState.value = CaptureState.Saved(
                    msg = if (isNew) {
                        "New barcode saved: $normalized"
                    } else {
                        "Barcode found in existing database: $normalized"
                    },
                    recordId = recordId,
                    isNewRecord = isNew
                )


                // Reload database info
                loadDatabaseInfo()

                // Reset after 2 seconds
                kotlinx.coroutines.delay(2000)
                _captureState.value = CaptureState.Idle
                _lastDetection.value = null
                pendingDetection = null

            } catch (error: Exception) {
                _captureState.value = CaptureState.Error("Failed to save: ${error.message}")
            }
        }
    }

    private fun loadDatabaseInfo() {
        viewModelScope.launch {
            repository.skuRecords.collectLatest { records ->
                _databaseInfo.value = SkuDatabaseInfo(
                    totalCount = records.size,
                    uniqueCount = records.map { it.barcode }.distinct().size
                )
            }
        }
    }

    companion object {
        fun Factory(
            repository: SirimRepository,
            analyzer: BarcodeAnalyzer,
            appScope: CoroutineScope
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SkuScannerViewModel(repository, analyzer, appScope) as T
            }
        }
    }
}

sealed class CaptureState(val message: String) {
    data object Idle : CaptureState("Align barcode within the guide")
    data class Ready(val msg: String) : CaptureState(msg)
    data class Processing(val msg: String) : CaptureState(msg)
    data class Saved(
        val msg: String,
        val recordId: Long?,
        val isNewRecord: Boolean
    ) : CaptureState(msg)
    data class Error(val msg: String) : CaptureState(msg)
}

data class BarcodeDetectionInfo(
    val value: String,
    val format: String
)

data class SkuDatabaseInfo(
    val totalCount: Int,
    val uniqueCount: Int
)
