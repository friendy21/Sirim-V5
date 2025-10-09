package com.sirim.scanner.ui.screens.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sirim.scanner.data.db.StorageRecord
import com.sirim.scanner.data.repository.SirimRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class StorageHubViewModel private constructor(
    repository: SirimRepository
) : ViewModel() {

    val storageRecords: StateFlow<List<StorageRecord>> = repository.storageRecords
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    companion object {
        fun Factory(repository: SirimRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return StorageHubViewModel(repository) as T
                }
            }
    }
}
