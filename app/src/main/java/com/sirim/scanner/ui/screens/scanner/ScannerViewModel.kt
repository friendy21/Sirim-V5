package com.sirim.scanner.ui.screens.scanner

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sirim.scanner.data.db.SirimRecord
import com.sirim.scanner.data.ocr.FieldConfidence
import com.sirim.scanner.data.ocr.OcrFailureReason
import com.sirim.scanner.data.ocr.LabelAnalyzer
import com.sirim.scanner.data.ocr.OcrResult
import com.sirim.scanner.data.ocr.toJpegByteArray
import com.sirim.scanner.data.repository.SirimRepository
import com.sirim.scanner.data.validation.FieldValidator
import com.sirim.scanner.data.validation.ValidationResult
import com.sirim.scanner.ui.model.ScanDraft
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ScannerViewModel private constructor(
    private val repository: SirimRepository,
    private val analyzer: LabelAnalyzer,
    private val appScope: CoroutineScope
) : ViewModel() {

    private val processing = AtomicBoolean(false)

    private val _extractedFields = MutableStateFlow<Map<String, FieldConfidence>>(emptyMap())
    val extractedFields: StateFlow<Map<String, FieldConfidence>> = _extractedFields.asStateFlow()

    private val _validationWarnings = MutableStateFlow<Map<String, String>>(emptyMap())
    val validationWarnings: StateFlow<Map<String, String>> = _validationWarnings.asStateFlow()

    private val _validationErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val validationErrors: StateFlow<Map<String, String>> = _validationErrors.asStateFlow()

    private val _status = MutableStateFlow(ScanStatus())
    val status: StateFlow<ScanStatus> = _status.asStateFlow()

    private val _ocrDebugInfo = MutableStateFlow(OcrDebugInfo())
    val ocrDebugInfo: StateFlow<OcrDebugInfo> = _ocrDebugInfo.asStateFlow()

    private val _lastSavedDraft = MutableStateFlow<ScanDraft?>(null)
    val lastSavedDraft: StateFlow<ScanDraft?> = _lastSavedDraft.asStateFlow()

    private val _duplicateScanState = MutableStateFlow<DuplicateScanState?>(null)
    val duplicateScanState: StateFlow<DuplicateScanState?> = _duplicateScanState.asStateFlow()

    private val duplicateCandidate = MutableStateFlow<DuplicateCandidate?>(null)

    private val _batchMode = MutableStateFlow(false)
    private val _batchQueue = MutableStateFlow<List<PendingRecord>>(emptyList())
    private val batchMutex = Mutex()

    private val _captureReviewState = MutableStateFlow<CaptureReviewState>(CaptureReviewState.Live)
    val captureReviewState: StateFlow<CaptureReviewState> = _captureReviewState.asStateFlow()

    private var latestPending: PendingRecord? = null
    private var reviewPending: PendingRecord? = null

    val batchUiState: StateFlow<BatchUiState> = combine(_batchMode, _batchQueue) { enabled, queue ->
        BatchUiState(
            enabled = enabled,
            queued = queue.map { BatchQueueItem(it.serial, it.timestamp, it.captureConfidence) }
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BatchUiState())

    fun setBatchMode(enabled: Boolean) {
        if (_batchMode.value == enabled) return
        _batchMode.value = enabled
        _status.value = _status.value.copy(
            state = if (enabled) ScanState.Ready else ScanState.Idle,
            message = if (enabled) "Batch mode enabled" else "Batch mode disabled"
        )
    }

    fun clearBatchQueue() {
        viewModelScope.launch {
            val cleared = batchMutex.withLock {
                if (_batchQueue.value.isEmpty()) {
                    return@withLock false
                }
                _batchQueue.value = emptyList()
                true
            }
            if (cleared) {
                _status.value = _status.value.copy(state = ScanState.Idle, message = "Batch queue cleared")
            }
        }
    }

    fun saveBatch() {
        appScope.launch {
            val queued = batchMutex.withLock {
                if (_batchQueue.value.isEmpty()) {
                    return@withLock emptyList()
                }
                val snapshot = _batchQueue.value
                _batchQueue.value = emptyList()
                snapshot
            }
            if (queued.isEmpty()) {
                _status.value = _status.value.copy(message = "Batch queue is empty")
                return@launch
            }
            val savedIds = mutableListOf<Long>()
            val skippedSerials = mutableListOf<String>()
            var totalConfidence = 0f
            var lastDraft: ScanDraft? = null
            queued.forEach { pending ->
                val duplicate = repository.findBySerial(pending.serial)
                if (duplicate != null) {
                    skippedSerials += pending.serial
                    return@forEach
                }
                val imagePath = pending.imageBytes?.let { repository.persistImage(it) }
                val record = pending.toRecord(imagePath)
                val id = repository.upsert(record)
                savedIds += id
                totalConfidence += pending.captureConfidence
                lastDraft = pending.toDraft(id, imagePath)
            }
            _batchMode.value = false
            if (savedIds.isNotEmpty()) {
                _lastSavedDraft.value = lastDraft
            }
            val message = when {
                savedIds.isNotEmpty() && skippedSerials.isNotEmpty() ->
                    "${savedIds.size} saved, ${skippedSerials.size} duplicates skipped"
                savedIds.isNotEmpty() -> "Batch saved (${savedIds.size} records)"
                skippedSerials.isNotEmpty() -> "Skipped duplicates: ${skippedSerials.joinToString()}"
                else -> "No records saved"
            }
            val averageConfidence = if (savedIds.isNotEmpty()) {
                (totalConfidence / savedIds.size).coerceIn(0f, 1f)
            } else {
                _status.value.confidence
            }
            _status.value = _status.value.copy(
                state = if (skippedSerials.isEmpty()) ScanState.Persisted else ScanState.Duplicate,
                message = message,
                confidence = averageConfidence
            )
        }
    }

    fun analyzeImage(imageProxy: ImageProxy) {
        if (_captureReviewState.value !is CaptureReviewState.Live) {
            imageProxy.close()
            return
        }
        if (!processing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        try {
            viewModelScope.launch {
                try {
                    val result = analyzer.analyze(imageProxy)
                    if (result !is OcrResult.Skipped) {
                        _status.value = _status.value.copy(
                            state = ScanState.Scanning,
                            message = "Analyzing frame...",
                            confidence = 0f
                        )
                    }
                    when (result) {
                        is OcrResult.Success -> handleResult(result)
                        is OcrResult.Partial -> handleResult(result)
                        is OcrResult.Failure -> handleFailure(result)
                        OcrResult.Empty -> {
                            _status.value = ScanStatus(state = ScanState.Idle, message = "Align the label within the guide")
                            _extractedFields.value = emptyMap()
                            _validationWarnings.value = emptyMap()
                            _validationErrors.value = emptyMap()
                        }
                        OcrResult.Skipped -> {
                            // No-op: frame skipped to throttle analysis rate.
                        }
                    }
                } catch (ex: Exception) {
                    _status.value = ScanStatus(state = ScanState.Error, message = "Scan failed: ${ex.message ?: "Unknown error"}")
                } finally {
                    imageProxy.close()
                    processing.set(false)
                }
            }
        } catch (ex: Exception) {
            imageProxy.close()
            processing.set(false)
            throw ex
        }
    }

    private suspend fun handleResult(result: OcrResult) {
        try {
            // Extract debug information
            val startTime = System.currentTimeMillis()

            val fields = when (result) {
                is OcrResult.Success -> result.fields
                is OcrResult.Partial -> result.fields
                else -> emptyMap()
            }
            if (fields.isEmpty()) {
                _status.value = ScanStatus(state = ScanState.Scanning, message = "Still searching for readable text")
                latestPending = null
                return
            }

            val validation = FieldValidator.validate(fields)
            _extractedFields.value = validation.sanitized
            _validationWarnings.value = validation.warnings
            _validationErrors.value = validation.errors

            // Update debug info
            val rawText = fields.values.joinToString("\n") { it.value }
            val detectedWords = rawText.split(Regex("\\s+")).filter { it.isNotBlank() }
            val qrCode = when (result) {
                is OcrResult.Success -> result.qrCode
                is OcrResult.Partial -> result.qrCode
                else -> null
            }

            _ocrDebugInfo.value = OcrDebugInfo(
                rawText = rawText,
                detectedWords = detectedWords,
                qrCodeValue = qrCode,
                barcodeFormat = if (qrCode != null) "QR Code" else null,
                processingTimeMs = System.currentTimeMillis() - startTime
            )

            val baseStatus = when (result) {
                is OcrResult.Success -> ScanState.Ready
                is OcrResult.Partial -> ScanState.Partial
                else -> ScanState.Scanning
            }

            val message = when {
                validation.errors.isNotEmpty() -> "Review highlighted fields"
                result is OcrResult.Partial -> "Hold steady for clearer capture"
                else -> "Tap capture to review"
            }

            _status.value = ScanStatus(
                state = baseStatus,
                message = message,
                confidence = when (result) {
                    is OcrResult.Success -> result.confidence
                    is OcrResult.Partial -> result.confidence
                    else -> 0f
                },
                frames = when (result) {
                    is OcrResult.Success -> result.frames
                    is OcrResult.Partial -> result.frames
                    else -> 0
                }
            )

            latestPending = buildPending(validation, result)
        } finally {
            recycleResultBitmap(result)
        }
    }

    private fun buildPending(validation: ValidationResult, result: OcrResult): PendingRecord? {
        val serialConfidence = validation.sanitized["sirimSerialNo"] ?: return null
        if (serialConfidence.value.isBlank()) {
            return null
        }
        val imageBytes = when (result) {
            is OcrResult.Success -> result.bitmap?.toJpegByteArray()
            is OcrResult.Partial -> result.bitmap?.toJpegByteArray()
            else -> null
        }
        val confidence = when (result) {
            is OcrResult.Success -> result.confidence
            is OcrResult.Partial -> result.confidence
            else -> 0f
        }
        return PendingRecord(
            serial = serialConfidence.value,
            fields = validation.sanitized.mapValues { it.value.copy() },
            imageBytes = imageBytes,
            timestamp = System.currentTimeMillis(),
            captureConfidence = confidence
        )
    }

    private fun handleFailure(result: OcrResult.Failure) {
        _extractedFields.value = emptyMap()
        _validationWarnings.value = emptyMap()
        _validationErrors.value = emptyMap()
        val (state, message) = when (result.reason) {
            OcrFailureReason.Preprocessing -> ScanState.Error to "Unable to stabilise the frame. Adjust distance or lighting."
        }
        _status.value = ScanStatus(state = state, message = message, confidence = 0f, frames = 0)
    }

    private suspend fun processPending(pending: PendingRecord) {
        val duplicate = repository.findBySerial(pending.serial)
        if (duplicate != null) {
            duplicateCandidate.value = DuplicateCandidate(pending, duplicate)
            _duplicateScanState.value = DuplicateScanState(serial = pending.serial)
            _status.value = _status.value.copy(
                state = ScanState.Duplicate,
                message = "Duplicate serial detected: ${pending.serial}",
                confidence = pending.captureConfidence
            )
            return
        }

        if (_batchMode.value) {
            val queueSize = addToBatchQueue(pending)
            if (queueSize == null) {
                _status.value = _status.value.copy(
                    state = ScanState.Duplicate,
                    message = "${pending.serial} already queued",
                    confidence = pending.captureConfidence
                )
            } else {
                _status.value = _status.value.copy(
                    state = ScanState.Ready,
                    message = "Queued ${pending.serial} ($queueSize pending)",
                    confidence = pending.captureConfidence
                )
            }
            return
        }

        persistPending(pending)
    }

    private suspend fun persistPending(pending: PendingRecord) {
        val imagePath = pending.imageBytes?.let { repository.persistImage(it) }
        val record = pending.toRecord(imagePath)
        val id = repository.upsert(record)
        _lastSavedDraft.value = pending.toDraft(id, imagePath)
        _status.value = _status.value.copy(
            state = ScanState.Persisted,
            message = "Record saved (${pending.serial})",
            confidence = pending.captureConfidence
        )
    }

    fun onCapture(imageBytes: ByteArray?) {
        if (_captureReviewState.value !is CaptureReviewState.Live) {
            return
        }
        val fields = _extractedFields.value
        val pending = latestPending ?: run {
            _status.value = _status.value.copy(
                state = ScanState.Error,
                message = "No readable serial detected"
            )
            return
        }
        if (fields.isEmpty()) {
            _status.value = _status.value.copy(
                state = ScanState.Error,
                message = "No fields available to capture"
            )
            return
        }
        val combined = pending.copy(imageBytes = imageBytes ?: pending.imageBytes)
        reviewPending = combined
        _captureReviewState.value = CaptureReviewState.Review(
            imageBytes = combined.imageBytes,
            fields = fields,
            warnings = _validationWarnings.value,
            errors = _validationErrors.value,
            confidence = combined.captureConfidence
        )
        _status.value = _status.value.copy(
            state = ScanState.Ready,
            message = if (_validationErrors.value.isEmpty()) {
                "Review capture before saving"
            } else {
                "Fix highlighted issues before saving"
            },
            confidence = combined.captureConfidence
        )
    }

    fun onRetake() {
        reviewPending = null
        latestPending = null
        _captureReviewState.value = CaptureReviewState.Live
        _status.value = _status.value.copy(
            state = ScanState.Idle,
            message = "Align the label within the guide"
        )
    }

    fun onConfirm() {
        val pending = reviewPending ?: run {
            _status.value = _status.value.copy(
                state = ScanState.Error,
                message = "Nothing to save"
            )
            return
        }
        if (_validationErrors.value.isNotEmpty()) {
            _status.value = _status.value.copy(
                state = ScanState.Error,
                message = "Resolve validation errors before saving"
            )
            return
        }
        _captureReviewState.value = CaptureReviewState.Saving
        reviewPending = null
        latestPending = null
        appScope.launch {
            try {
                processPending(pending)
            } finally {
                _captureReviewState.value = CaptureReviewState.Live
            }
        }
    }

    fun onCaptureError(message: String) {
        _captureReviewState.value = CaptureReviewState.Live
        _status.value = ScanStatus(state = ScanState.Error, message = message)
    }

    fun clearLastSavedDraft() {
        _lastSavedDraft.value = null
    }

    fun keepDuplicate() {
        val candidate = duplicateCandidate.value ?: return
        appScope.launch {
            try {
                val imagePath = candidate.pending.imageBytes?.let { repository.persistImage(it) }
                val record = candidate.pending.toRecord(
                    imagePath = imagePath,
                    duplicate = true,
                    base = candidate.existing
                )
                val id = repository.markDuplicate(record)
                _lastSavedDraft.value = candidate.pending.toDraft(id, imagePath)
                _status.value = _status.value.copy(
                    state = ScanState.Persisted,
                    message = "Duplicate saved (${candidate.pending.serial})",
                    confidence = candidate.pending.captureConfidence
                )
                clearDuplicateCache()
            } catch (error: Exception) {
                _status.value = ScanStatus(
                    state = ScanState.Error,
                    message = "Failed to save duplicate: ${error.message ?: "Unknown error"}",
                    confidence = candidate.pending.captureConfidence
                )
            }
        }
    }

    fun discardDuplicate() {
        val candidate = duplicateCandidate.value ?: return
        val serial = candidate.pending.serial
        val confidence = candidate.pending.captureConfidence
        clearDuplicateCache()
        _status.value = _status.value.copy(
            state = ScanState.Idle,
            message = "Duplicate discarded ($serial)",
            confidence = confidence
        )
    }

    companion object {
        fun Factory(
            repository: SirimRepository,
            analyzer: LabelAnalyzer,
            appScope: CoroutineScope
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ScannerViewModel(repository, analyzer, appScope) as T
            }
        }
    }

    private suspend fun addToBatchQueue(pending: PendingRecord): Int? = batchMutex.withLock {
        if (_batchQueue.value.any { it.serial.equals(pending.serial, ignoreCase = true) }) {
            return@withLock null
        }
        val updated = _batchQueue.value + pending
        _batchQueue.value = updated
        updated.size
    }

    private fun clearDuplicateCache() {
        duplicateCandidate.value = null
        _duplicateScanState.value = null
    }

    private fun recycleResultBitmap(result: OcrResult) {
        when (result) {
            is OcrResult.Success -> result.bitmap?.recycleSafely()
            is OcrResult.Partial -> result.bitmap?.recycleSafely()
            else -> Unit
        }
    }
}

private fun Bitmap?.recycleSafely() {
    if (this != null && !isRecycled) {
        recycle()
    }
}
data class ScanStatus(
    val state: ScanState = ScanState.Idle,
    val message: String = "",
    val confidence: Float = 0f,
    val frames: Int = 0
)

enum class ScanState {
    Idle,
    Scanning,
    Partial,
    Ready,
    Persisted,
    Duplicate,
    Error
}

data class BatchUiState(
    val enabled: Boolean = false,
    val queued: List<BatchQueueItem> = emptyList()
)

data class BatchQueueItem(
    val serial: String,
    val capturedAt: Long,
    val confidence: Float
)

data class DuplicateScanState(
    val serial: String
)
sealed class CaptureReviewState {
    data object Live : CaptureReviewState()

    data class Review(
        val imageBytes: ByteArray?,
        val fields: Map<String, FieldConfidence>,
        val warnings: Map<String, String>,
        val errors: Map<String, String>,
        val confidence: Float
    ) : CaptureReviewState()

    data object Saving : CaptureReviewState()
}

private data class PendingRecord(
    val serial: String,
    val fields: Map<String, FieldConfidence>,
    val imageBytes: ByteArray?,
    val timestamp: Long,
    val captureConfidence: Float
) {
    fun toRecord(
        imagePath: String?,
        duplicate: Boolean = false,
        base: SirimRecord? = null
    ): SirimRecord {
        val resolvedImagePath = imagePath ?: base?.imagePath
        return SirimRecord(
            id = base?.id ?: 0,
            sirimSerialNo = fields["sirimSerialNo"]?.value.orEmpty(),
            batchNo = fields["batchNo"]?.value.orEmpty(),
            brandTrademark = fields["brandTrademark"]?.value.orEmpty(),
            model = fields["model"]?.value.orEmpty(),
            type = fields["type"]?.value.orEmpty(),
            rating = fields["rating"]?.value.orEmpty(),
            size = fields["size"]?.value.orEmpty(),
            imagePath = resolvedImagePath,
            customFields = base?.customFields,
            captureConfidence = captureConfidence,
            createdAt = timestamp,
            isVerified = base?.isVerified ?: false,
            isDuplicate = duplicate || base?.isDuplicate == true,
            needsSync = true,
            serverId = base?.serverId,
            lastSynced = base?.lastSynced
        )
    }

    fun toDraft(recordId: Long, imagePath: String?): ScanDraft {
        val values = LinkedHashMap<String, String>()
        val confidences = LinkedHashMap<String, Float>()
        fields.forEach { (key, confidence) ->
            values[key] = confidence.value
            confidences[key] = confidence.confidence
        }
        return ScanDraft(
            recordId = recordId,
            fieldValues = values,
            fieldConfidences = confidences,
            captureConfidence = captureConfidence,
            imagePath = imagePath
        )
    }
}

private data class DuplicateCandidate(
    val pending: PendingRecord,
    val existing: SirimRecord
)

