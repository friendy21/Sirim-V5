package com.sirim.scanner.ui.screens.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sirim.scanner.data.db.SkuExportRecord
import com.sirim.scanner.data.db.StorageRecord
import com.sirim.scanner.data.repository.SirimRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StorageHubViewModel private constructor(
    private val repository: SirimRepository
) : ViewModel() {

    val storageRecords: StateFlow<List<StorageRecord>> = repository.storageRecords
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun clearSirimDatabase() {
        viewModelScope.launch { repository.clearSirim() }
    }

    fun deleteSkuExport(record: SkuExportRecord) {
        viewModelScope.launch { repository.deleteSkuExport(record) }
    }

    companion object {
        fun Factory(repository: SirimRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return StorageHubViewModel(repository) as T
                }
            }
    }
}
