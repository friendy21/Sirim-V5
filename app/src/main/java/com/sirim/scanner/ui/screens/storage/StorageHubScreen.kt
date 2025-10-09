package com.sirim.scanner.ui.screens.storage

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sirim.scanner.R
import com.sirim.scanner.data.db.StorageRecord
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageHubScreen(
    viewModel: StorageHubViewModel,
    isSessionValid: Boolean,
    onRequireAuthentication: (() -> Unit) -> Unit,
    onBack: () -> Unit,
    onOpenSirimScanner: () -> Unit,
    onOpenSkuScanner: () -> Unit,
    onViewSirimRecords: () -> Unit,
    onEditSirimRecords: () -> Unit,
    onShareSirimRecords: () -> Unit
) {
    val records by viewModel.storageRecords.collectAsState()
    val context = LocalContext.current
    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    fun requireAdmin(action: () -> Unit) {
        if (isSessionValid) {
            action()
        } else {
            onRequireAuthentication(action)
        }
    }

    fun viewExport(record: StorageRecord.SkuExport) {
        val uri = Uri.parse(record.export.uri)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                uri,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { activityLauncher.launch(intent) }
            .onFailure {
                Toast.makeText(context, R.string.storage_export_view_error, Toast.LENGTH_LONG).show()
            }
    }

    fun shareExport(record: StorageRecord.SkuExport) {
        val uri = Uri.parse(record.export.uri)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activityLauncher.launch(Intent.createChooser(intent, context.getString(R.string.storage_share_export)))
    }

    fun editExport(record: StorageRecord.SkuExport) {
        val uri = Uri.parse(record.export.uri)
        val intent = Intent(Intent.ACTION_EDIT).apply {
            setDataAndType(
                uri,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { activityLauncher.launch(intent) }
            .onFailure {
                Toast.makeText(context, R.string.storage_export_edit_error, Toast.LENGTH_LONG).show()
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.storage_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (records.isEmpty()) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = stringResource(id = R.string.storage_empty_title), fontWeight = FontWeight.SemiBold)
                Text(
                    text = stringResource(id = R.string.storage_empty_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                items(records, key = { it.id }) { record ->
                    when (record) {
                        is StorageRecord.SirimDatabase -> {
                            StorageHubCard(
                                title = record.title,
                                description = record.description,
                                updatedAt = record.createdAt,
                                scannerLabel = R.string.storage_action_scanner,
                                onScanner = onOpenSirimScanner,
                                onView = onViewSirimRecords,
                                onShare = { requireAdmin(onShareSirimRecords) },
                                onEdit = { requireAdmin(onEditSirimRecords) },
                                onDelete = { requireAdmin { viewModel.clearSirimDatabase() } }
                            )
                        }

                        is StorageRecord.SkuExport -> {
                            StorageHubCard(
                                title = stringResource(
                                    id = R.string.storage_sku_export_title,
                                    record.export.fileName
                                ),
                                description = stringResource(
                                    id = R.string.storage_sku_export_description,
                                    record.export.recordCount
                                ),
                                updatedAt = record.createdAt,
                                scannerLabel = R.string.storage_action_sku_scanner,
                                onScanner = onOpenSkuScanner,
                                onView = { viewExport(record) },
                                onShare = { requireAdmin { shareExport(record) } },
                                onEdit = { requireAdmin { editExport(record) } },
                                onDelete = { requireAdmin { viewModel.deleteSkuExport(record.export) } }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageHubCard(
    title: String,
    description: String,
    updatedAt: Long,
    @StringRes scannerLabel: Int,
    onScanner: () -> Unit,
    onView: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(text = description, style = MaterialTheme.typography.bodyMedium)
                if (updatedAt > 0) {
                    Text(
                        text = "Updated ${formatTimestamp(updatedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onScanner, modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(id = scannerLabel))
                }
                Button(onClick = onView, modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(id = R.string.storage_action_view))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onShare, modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(id = R.string.storage_action_share))
                }
                Button(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(id = R.string.storage_action_edit))
                }
                Button(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text(text = stringResource(id = R.string.storage_action_delete))
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
