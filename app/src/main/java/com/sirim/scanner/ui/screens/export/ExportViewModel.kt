package com.sirim.scanner.ui.screens.export

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sirim.scanner.data.db.SirimRecord
import com.sirim.scanner.data.export.ExportFormat
import com.sirim.scanner.data.export.ExportManager
import com.sirim.scanner.data.repository.SirimRepository
import com.sirim.scanner.ui.common.DateRangeFilter
import com.sirim.scanner.ui.common.VerifiedFilter
import com.sirim.scanner.ui.common.startTimestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExportViewModel private constructor(
    private val repository: SirimRepository,
    private val exportManager: ExportManager
) : ViewModel() {

    private val allRecords: StateFlow<List<SirimRecord>> = repository.records
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val filters = MutableStateFlow(ExportFilters())

    val uiState: StateFlow<ExportUiState> = combine(allRecords, filters) { records, filters ->
        val start = filters.dateRange.startTimestamp()
        val filtered = records.filter { record ->
            val matchesVerified = when (filters.verified) {
                VerifiedFilter.ALL -> true
                VerifiedFilter.VERIFIED -> record.isVerified
                VerifiedFilter.UNVERIFIED -> !record.isVerified
            }
            val matchesBrand = filters.brand?.let { record.brandTrademark.equals(it, ignoreCase = true) } ?: true
            val matchesDate = start?.let { record.createdAt >= it } ?: true
            matchesVerified && matchesBrand && matchesDate
        }
        val availableBrands = records.mapNotNull { it.brandTrademark.takeIf(String::isNotBlank) }
            .distinct()
            .sorted()
        ExportUiState(
            filteredRecords = filtered,
            totalRecords = records.size,
            filters = filters,
            availableBrands = availableBrands
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, ExportUiState())
    private val _lastExportUri = MutableStateFlow<Uri?>(null)
    val lastExportUri: StateFlow<Uri?> = _lastExportUri.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _lastFormat = MutableStateFlow<ExportFormat?>(null)
    val lastFormat: StateFlow<ExportFormat?> = _lastFormat.asStateFlow()

    fun export(format: ExportFormat) {
        if (_isExporting.value) return
        _isExporting.value = true
        viewModelScope.launch {
            val snapshot = uiState.value
            val uri = when (format) {
                ExportFormat.Pdf -> exportManager.exportToPdf(snapshot.filteredRecords)
                ExportFormat.Excel -> exportManager.exportToExcel(snapshot.filteredRecords)
                ExportFormat.Csv -> exportManager.exportToCsv(snapshot.filteredRecords)
            }
            _lastExportUri.value = uri
            _lastFormat.value = format
            _isExporting.value = false
        }
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
        filters.value = ExportFilters()
    }

    companion object {
        fun Factory(
            repository: SirimRepository,
            exportManager: ExportManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ExportViewModel(repository, exportManager) as T
            }
        }
    }
}
data class ExportUiState(
    val filteredRecords: List<SirimRecord> = emptyList(),
    val totalRecords: Int = 0,
    val filters: ExportFilters = ExportFilters(),
    val availableBrands: List<String> = emptyList()
)

data class ExportFilters(
    val verified: VerifiedFilter = VerifiedFilter.ALL,
    val brand: String? = null,
    val dateRange: DateRangeFilter = DateRangeFilter.ALL
)
