package com.sirim.scanner.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sirim.scanner.BuildConfig
import com.sirim.scanner.data.preferences.StartupPage
import com.sirim.scanner.data.preferences.UserPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    authError: String?,
    onStartupSelected: (StartupPage) -> Unit,
    onAuthenticate: (String, String) -> Unit,
    onLogout: () -> Unit,
    onDismissAuthError: () -> Unit,
    onBack: () -> Unit,
    onOpenFeedback: () -> Unit
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(preferences.isSessionValid()) {
        if (preferences.isSessionValid()) {
            username = ""
            password = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            StartupPreferenceCard(
                selected = preferences.startupPage,
                onSelected = onStartupSelected
            )

            AdminAccessCard(
                isAuthenticated = preferences.isSessionValid(),
                remainingMillis = preferences.remainingSessionTimeMillis(),
                username = username,
                password = password,
                onUsernameChange = { username = it },
                onPasswordChange = { password = it },
                onAuthenticate = {
                    onAuthenticate(username, password)
                    if (authError == null) {
                        password = ""
                    }
                },
                onLogout = onLogout,
                authError = authError,
                onDismissError = onDismissAuthError
            )

            AboutCard(onOpenFeedback = onOpenFeedback)
        }
    }
}

@Composable
private fun StartupPreferenceCard(
    selected: StartupPage,
    onSelected: (StartupPage) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Startup page", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            Text(
                text = "Choose where the app opens the next time you launch it.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
            StartupPage.entries.forEach { option ->
                val selectedOption = option == selected
                StartupOptionRow(
                    label = when (option) {
                        StartupPage.AskEveryTime -> "Ask every time"
                        StartupPage.SirimScanner -> "SIRIM Scanner"
                        StartupPage.SkuScanner -> "SKU Scanner"
                        StartupPage.Storage -> "Storage"
                    },
                    selected = selectedOption,
                    onClick = { onSelected(option) }
                )
            }
        }
    }
}

@Composable
private fun StartupOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val textStyle = androidx.compose.material3.MaterialTheme.typography.bodyLarge
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        Column {
            Text(text = label, style = textStyle)
        }
    }
}

@Composable
private fun AdminAccessCard(
    isAuthenticated: Boolean,
    remainingMillis: Long,
    username: String,
    password: String,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onAuthenticate: () -> Unit,
    onLogout: () -> Unit,
    authError: String?,
    onDismissError: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Admin access", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            StatusRow(isAuthenticated = isAuthenticated, remainingMillis = remainingMillis)
            if (isAuthenticated) {
                Text(
                    text = "You are currently authenticated. Protected actions remain unlocked until the session expires.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onLogout) { Text("Log out") }
            } else {
                if (!authError.isNullOrBlank()) {
                    ErrorMessage(message = authError, onDismiss = onDismissError)
                }
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                Button(
                    onClick = onAuthenticate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Log in as admin")
                }
            }
        }
    }
}

@Composable
private fun StatusRow(isAuthenticated: Boolean, remainingMillis: Long) {
    val icon = if (isAuthenticated) Icons.Outlined.LockOpen else Icons.Outlined.Lock
    val color = if (isAuthenticated) Color(0xFF2E7D32) else Color(0xFFB71C1C)
    val text = if (isAuthenticated) {
        val minutes = (remainingMillis / 60000L).coerceAtLeast(0L)
        "Admin session active${if (minutes > 0) ": expires in $minutes min" else ""}"
    } else {
        "Admin session not active"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(imageVector = icon, contentDescription = null, tint = color)
        Text(text = text, color = color, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorMessage(message: String, onDismiss: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = Color(0xFFB71C1C))
                Text(text = message, color = Color(0xFFB71C1C))
            }
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

@Composable
private fun AboutCard(onOpenFeedback: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("About", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                Column {
                    Text(text = "SIRIM Scanner v2", fontWeight = FontWeight.SemiBold)
                    Text(text = "Build ${BuildConfig.VERSION_NAME}", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(
                onClick = onOpenFeedback,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Send feedback")
            }
        }
    }
}
