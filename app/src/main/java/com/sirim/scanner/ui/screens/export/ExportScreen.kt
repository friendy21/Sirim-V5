package com.sirim.scanner.ui.screens.export

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sirim.scanner.data.export.ExportFormat
import com.sirim.scanner.ui.common.DateRangeFilter
import com.sirim.scanner.ui.common.VerifiedFilter


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    viewModel: ExportViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isExporting by viewModel.isExporting.collectAsState()
    val lastExportUri by viewModel.lastExportUri.collectAsState()
    val lastFormat by viewModel.lastFormat.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    LaunchedEffect(lastExportUri) {
        lastExportUri?.let { uri ->
            context.grantUriPermission(
                context.packageName,
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export Records") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            ExportFilterSection(
                uiState = uiState,
                onVerifiedChange = viewModel::setVerifiedFilter,
                onBrandChange = viewModel::setBrandFilter,
                onDateChange = viewModel::setDateRange,
                onClear = viewModel::clearFilters
            )

            Text(
                text = "Preparing ${uiState.filteredRecords.size} of ${uiState.totalRecords} records",
                modifier = Modifier.padding(top = 4.dp)
            )

            Button(
                onClick = { viewModel.export(ExportFormat.Pdf) },
                enabled = !isExporting && uiState.filteredRecords.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Export as PDF") }
            Button(
                onClick = { viewModel.export(ExportFormat.Excel) },
                enabled = !isExporting && uiState.filteredRecords.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Export as Excel") }
            Button(
                onClick = { viewModel.export(ExportFormat.Csv) },
                enabled = !isExporting && uiState.filteredRecords.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Export as CSV") }

            if (lastExportUri != null) {
                Text("Last export ready at: $lastExportUri")
                Button(
                    onClick = {
                        val format = lastFormat ?: ExportFormat.Pdf
                        val mime = when (format) {
                            ExportFormat.Pdf -> "application/pdf"
                            ExportFormat.Excel -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                            ExportFormat.Csv -> "text/csv"
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = mime
                            putExtra(Intent.EXTRA_STREAM, lastExportUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        val chooser = Intent.createChooser(intent, "Share export")
                        shareLauncher.launch(chooser)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Share Export")
                }
            }
        }
    }
}

@Composable
private fun ExportFilterSection(
    uiState: ExportUiState,
    onVerifiedChange: (VerifiedFilter) -> Unit,
    onBrandChange: (String?) -> Unit,
    onDateChange: (DateRangeFilter) -> Unit,
    onClear: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            TextButton(onClick = onClear) { Text("Reset") }
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
