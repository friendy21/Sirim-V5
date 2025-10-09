package com.sirim.scanner.ui.screens.startup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun StartupScreen(
    modifier: Modifier = Modifier,
    onOpenSirimScanner: () -> Unit,
    onOpenSkuScanner: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top)
        ) {
            Text(
                text = "What would you like to do?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Choose a starting point to begin scanning or reviewing your records.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            StartupOptionCard(
                title = "SIRIM Scanner",
                description = "Scan certification labels with OCR and QR capture.",
                icon = { Icon(Icons.Outlined.QrCodeScanner, contentDescription = null) },
                onClick = onOpenSirimScanner
            )
            StartupOptionCard(
                title = "SKU Scanner",
                description = "Scan product barcodes for inventory tracking.",
                icon = { Icon(Icons.Outlined.Inventory2, contentDescription = null) },
                onClick = onOpenSkuScanner
            )
            StartupOptionCard(
                title = "Storage",
                description = "Review, filter, and manage your stored scans.",
                icon = { Icon(Icons.Outlined.Storage, contentDescription = null) },
                onClick = onOpenStorage
            )
            StartupOptionCard(
                title = "Settings",
                description = "Change startup preferences and manage admin access.",
                icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                onClick = onOpenSettings
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartupOptionCard(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            icon()
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
