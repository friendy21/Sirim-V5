package com.sirim.scanner.ui.screens.sku

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sirim.scanner.data.db.SkuRecord
import com.sirim.scanner.data.db.toGalleryJson
import com.sirim.scanner.data.db.toGalleryList
import com.sirim.scanner.data.repository.SirimRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SkuRecordFormViewModel private constructor(
    private val repository: SirimRepository
) : ViewModel() {

    private val _activeRecord = MutableStateFlow<SkuRecord?>(null)
    val activeRecord: StateFlow<SkuRecord?> = _activeRecord.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _formError = MutableStateFlow<String?>(null)
    val formError: StateFlow<String?> = _formError.asStateFlow()

    private val _galleryPaths = MutableStateFlow<List<String>>(emptyList())
    val galleryPaths: StateFlow<List<String>> = _galleryPaths.asStateFlow()

    fun loadRecord(id: Long) {
        viewModelScope.launch {
            val record = withContext(Dispatchers.IO) { repository.getSkuRecord(id) }
            _activeRecord.value = record
            _galleryPaths.value = record?.galleryPaths.toGalleryList()
            _formError.value = null
        }
    }

    fun reset() {
        _activeRecord.value = null
        _galleryPaths.value = emptyList()
        _formError.value = null
    }

    fun addGalleryImage(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) { repository.persistImage(bytes) }
            _galleryPaths.update { it + path }
        }
    }

    fun removeGalleryImage(path: String) {
        _galleryPaths.update { current -> current.filterNot { it == path } }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { File(path).takeIf(File::exists)?.delete() }
        }
    }

    fun saveRecord(
        barcode: String,
        batchNo: String?,
        brand: String?,
        model: String?,
        type: String?,
        rating: String?,
        size: String?,
        isVerified: Boolean,
        onSaved: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val normalizedBarcode = barcode.trim()
            if (normalizedBarcode.isBlank()) {
                _formError.value = "Barcode is required"
                return@launch
            }
            if (_isSaving.value) return@launch
            _isSaving.value = true
            try {
                val existing = withContext(Dispatchers.IO) { repository.findByBarcode(normalizedBarcode) }
                val currentId = _activeRecord.value?.id ?: 0L
                if (existing != null && existing.id != currentId) {
                    _formError.value = "Barcode $normalizedBarcode already exists"
                    return@launch
                }

                val base = _activeRecord.value ?: SkuRecord(barcode = normalizedBarcode)
                val sanitized = base.copy(
                    barcode = normalizedBarcode,
                    batchNo = batchNo?.takeIf { it.isNotBlank() },
                    brandTrademark = brand?.takeIf { it.isNotBlank() },
                    model = model?.takeIf { it.isNotBlank() },
                    type = type?.takeIf { it.isNotBlank() },
                    rating = rating?.takeIf { it.isNotBlank() },
                    size = size?.takeIf { it.isNotBlank() },
                    isVerified = isVerified,
                    needsSync = true,
                    galleryPaths = _galleryPaths.value.takeIf { it.isNotEmpty() }?.toGalleryJson()
                )
                val id = withContext(Dispatchers.IO) { repository.upsertSku(sanitized) }
                val refreshed = withContext(Dispatchers.IO) { repository.getSkuRecord(id) }
                _activeRecord.value = refreshed
                _galleryPaths.value = refreshed?.galleryPaths.toGalleryList()
                _formError.value = null
                onSaved(id)
            } catch (error: Exception) {
                _formError.value = "Failed to save: ${error.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    companion object {
        fun Factory(repository: SirimRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SkuRecordFormViewModel(repository) as T
                }
            }
    }
}
