package com.sirim.scanner.ui.screens.records
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sirim.scanner.data.db.SirimRecord
import com.sirim.scanner.ui.common.DateRangeFilter
import com.sirim.scanner.ui.common.VerifiedFilter

enum class SortOption {
    DATE_DESC,
    DATE_ASC,
    SERIAL_ASC,
    SERIAL_DESC,
    BRAND_ASC,
    BRAND_DESC
}

fun SortOption.label(): String = when (this) {
    SortOption.DATE_DESC -> "Date (Newest)"
    SortOption.DATE_ASC -> "Date (Oldest)"
    SortOption.SERIAL_ASC -> "Serial (A-Z)"
    SortOption.SERIAL_DESC -> "Serial (Z-A)"
    SortOption.BRAND_ASC -> "Brand (A-Z)"
    SortOption.BRAND_DESC -> "Brand (Z-A)"
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordListScreen(
    viewModel: RecordViewModel,
    onAdd: () -> Unit,
    onEdit: (SirimRecord) -> Unit,
    onBack: () -> Unit,
    onNavigateToExport: () -> Unit,
    isAuthenticated: Boolean,
    onRequireAuthentication: (() -> Unit) -> Unit,
    readOnly: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsState()
    val records = uiState.records

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!readOnly) {
                        IconButton(onClick = onNavigateToExport) {
                            Icon(Icons.Default.Download, contentDescription = "Export")
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!readOnly) {
                Button(
                    onClick = {
                        if (isAuthenticated) onAdd() else onRequireAuthentication(onAdd)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add Record") }

                if (!isAuthenticated) {
                    Text(
                        text = "Admin login is required to add, edit, or delete records.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::updateSearchQuery,
                label = { Text("Search records") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            var showSortMenu by remember { mutableStateOf(false) }

            Box {
                OutlinedButton(
                    onClick = { showSortMenu = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Sort, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sort: ${uiState.sortOption.label()}")
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    SortOption.values().forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label()) },
                            onClick = {
                                viewModel.setSortOption(option)
                                showSortMenu = false
                            }
                        )
                    }
                }
            }

            FilterSection(
                uiState = uiState,
                onVerifiedChange = viewModel::setVerifiedFilter,
                onBrandChange = viewModel::setBrandFilter,
                onDateChange = viewModel::setDateRange,
                onClear = viewModel::clearFilters
            )

            Text(
                text = "Showing ${records.size} of ${uiState.totalRecords} records",
                style = MaterialTheme.typography.bodyMedium
            )

            if (records.isEmpty()) {
                Text(
                    text = "No records match the current filters.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                val handleEdit: (SirimRecord) -> Unit = { record ->
                    if (readOnly || isAuthenticated) {
                        onEdit(record)
                    } else {
                        onRequireAuthentication { onEdit(record) }
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(records, key = { it.id }) { record ->
                        val deleteAction = if (readOnly) {
                            null
                        } else {
                            {
                                if (isAuthenticated) {
                                    viewModel.delete(record)
                                } else {
                                    onRequireAuthentication { viewModel.delete(record) }
                                }
                            }
                        }
                        RecordListItem(
                            record = record,
                            onClick = { handleEdit(record) },
                            onDelete = deleteAction,
                            isAuthenticated = isAuthenticated,
                            readOnly = readOnly
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterSection(
    uiState: RecordListUiState,
    onVerifiedChange: (VerifiedFilter) -> Unit,
    onBrandChange: (String?) -> Unit,
    onDateChange: (DateRangeFilter) -> Unit,
    onClear: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VerifiedFilter.values().forEach { filter ->
                FilterChip(
                    selected = uiState.filters.verified == filter,
                    onClick = { onVerifiedChange(filter) },
                    label = { Text(filterLabel(filter)) }
                )
            }
        }

        if (uiState.availableBrands.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = uiState.filters.brand == null,
                        onClick = { onBrandChange(null) },
                        label = { Text("All Brands") }
                    )
                }
                items(uiState.availableBrands) { brand ->
                    FilterChip(
                        selected = uiState.filters.brand.equals(brand, ignoreCase = true),
                        onClick = { onBrandChange(brand) },
                        label = { Text(brand) }
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateRangeFilter.values().forEach { range ->
                val selected = uiState.filters.dateRange == range
                FilterChip(
                    selected = selected,
                    onClick = { onDateChange(range) },
                    label = { Text(rangeLabel(range)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = if (selected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    )
                )
            }
            Spacer(modifier = Modifier.weight(1f, fill = true))
            TextButton(onClick = onClear) {
                Text("Reset")
            }
        }
    }
}

@Composable
private fun RecordListItem(
    record: SirimRecord,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
    isAuthenticated: Boolean,
    readOnly: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = record.sirimSerialNo, style = MaterialTheme.typography.titleMedium)
                Text(text = record.brandTrademark, style = MaterialTheme.typography.bodyMedium)
                Text(text = record.model, style = MaterialTheme.typography.bodySmall)
            }
            if (!readOnly && onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = if (isAuthenticated) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

private fun filterLabel(filter: VerifiedFilter): String = when (filter) {
    VerifiedFilter.ALL -> "All"
    VerifiedFilter.VERIFIED -> "Verified"
    VerifiedFilter.UNVERIFIED -> "Unverified"
}

private fun rangeLabel(range: DateRangeFilter): String = when (range) {
    DateRangeFilter.ALL -> "All Dates"
    DateRangeFilter.LAST_7_DAYS -> "Last 7 days"
    DateRangeFilter.LAST_30_DAYS -> "Last 30 days"
}
