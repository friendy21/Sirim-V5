package com.sirim.scanner.ui.screens.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sirim.scanner.data.db.SirimRecord
import com.sirim.scanner.data.repository.SirimRepository
import com.sirim.scanner.ui.common.DateRangeFilter
import com.sirim.scanner.ui.common.VerifiedFilter
import com.sirim.scanner.ui.common.startTimestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RecordViewModel private constructor(
    private val repository: SirimRepository
) : ViewModel() {

    private val allRecords: StateFlow<List<SirimRecord>> = repository.records
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val searchQuery = MutableStateFlow("")
    private val debouncedQuery = searchQuery
        .debounce(300)
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    private val filters = MutableStateFlow(RecordFilters())
    private val sortOption = MutableStateFlow(SortOption.DATE_DESC)

    val uiState: StateFlow<RecordListUiState> = combine(allRecords, debouncedQuery, filters, sortOption) { records, query, filters, sort ->
        val normalizedQuery = query.trim()
        val availableBrands = records.mapNotNull { it.brandTrademark.takeIf(String::isNotBlank) }
            .distinct()
            .sorted()
        val startTimestamp = filters.dateRange.startTimestamp()
        val filteredRecords = records.filter { record ->
            val matchesQuery = if (normalizedQuery.isBlank()) {
                true
            } else {
                listOf(
                    record.sirimSerialNo,
                    record.batchNo,
                    record.brandTrademark,
                    record.model,
                    record.type,
                    record.rating,
                    record.size
                ).any { value -> value.contains(normalizedQuery, ignoreCase = true) }
            }
            val matchesVerified = when (filters.verified) {
                VerifiedFilter.ALL -> true
                VerifiedFilter.VERIFIED -> record.isVerified
                VerifiedFilter.UNVERIFIED -> !record.isVerified
            }
            val matchesBrand = filters.brand?.let { record.brandTrademark.equals(it, ignoreCase = true) } ?: true
            val matchesDate = startTimestamp?.let { record.createdAt >= it } ?: true
            matchesQuery && matchesVerified && matchesBrand && matchesDate
        }
        val sortedRecords = when (sort) {
            SortOption.DATE_DESC -> filteredRecords.sortedByDescending { it.createdAt }
            SortOption.DATE_ASC -> filteredRecords.sortedBy { it.createdAt }
            SortOption.SERIAL_ASC -> filteredRecords.sortedBy { it.sirimSerialNo }
            SortOption.SERIAL_DESC -> filteredRecords.sortedByDescending { it.sirimSerialNo }
            SortOption.BRAND_ASC -> filteredRecords.sortedBy { it.brandTrademark }
            SortOption.BRAND_DESC -> filteredRecords.sortedByDescending { it.brandTrademark }
        }
        RecordListUiState(
            records = sortedRecords,
            availableBrands = availableBrands,
            query = normalizedQuery,
            filters = filters,
            totalRecords = records.size,
            sortOption = sort
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, RecordListUiState())

    private val _activeRecord = MutableStateFlow<SirimRecord?>(null)
    val activeRecord: StateFlow<SirimRecord?> = _activeRecord.asStateFlow()

    private val _formError = MutableStateFlow<String?>(null)
    val formError: StateFlow<String?> = _formError.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    fun loadRecord(id: Long) {
        viewModelScope.launch {
            _activeRecord.value = repository.getRecord(id)
            _formError.value = null
        }
    }

    fun resetActiveRecord() {
        _activeRecord.value = null
        _formError.value = null
    }

    fun createOrUpdate(record: SirimRecord, onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            val normalizedSerial = record.sirimSerialNo.trim()
            if (normalizedSerial.isBlank()) {
                _formError.value = "SIRIM serial number is required"
                return@launch
            }
            if (_isSaving.value) {
                return@launch
            }
            _isSaving.value = true
            try {
                val existing = repository.findBySerial(normalizedSerial)
                if (existing != null && existing.id != record.id) {
                    _formError.value = "Serial $normalizedSerial already exists"
                    return@launch
                }
                val sanitized = record.copy(sirimSerialNo = normalizedSerial)
                val id = repository.upsert(sanitized)
                _activeRecord.value = repository.getRecord(id)
                _formError.value = null
                onSaved(id)
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun delete(record: SirimRecord) {
        viewModelScope.launch {
            repository.delete(record)
        }
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setVerifiedFilter(filter: VerifiedFilter) {
        filters.update { it.copy(verified = filter) }
    }

    fun setBrandFilter(brand: String?) {
        filters.update { it.copy(brand = brand) }
    }

    fun setDateRange(range: DateRangeFilter) {
        filters.update { it.copy(dateRange = range) }
    }

    fun clearFilters() {
        filters.value = RecordFilters()
    }

    fun setSortOption(option: SortOption) {
        sortOption.value = option
    }

    fun clearFormError() {
        _formError.value = null
    }
    companion object {
        fun Factory(repository: SirimRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return RecordViewModel(repository) as T
                }
            }
    }
}

data class RecordListUiState(
    val records: List<SirimRecord> = emptyList(),
    val availableBrands: List<String> = emptyList(),
    val query: String = "",
    val filters: RecordFilters = RecordFilters(),
    val totalRecords: Int = 0,
    val sortOption: SortOption = SortOption.DATE_DESC
)

data class RecordFilters(
    val verified: VerifiedFilter = VerifiedFilter.ALL,
    val brand: String? = null,
    val dateRange: DateRangeFilter = DateRangeFilter.ALL
)
