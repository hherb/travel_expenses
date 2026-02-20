package com.travelexpenses.android.ui.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    var showPinDialog by remember { mutableStateOf(false) }
    var showAutoLockPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.exportArchive.collect { json ->
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, json)
                type = "application/json"
            }
            context.startActivity(Intent.createChooser(sendIntent, "Export Data"))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.exportCsv.collect { csv ->
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, csv)
                type = "text/csv"
            }
            context.startActivity(Intent.createChooser(sendIntent, "Export CSV"))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // Security section
            SectionHeader("Security")

            SettingsItem(
                icon = Icons.Default.Fingerprint,
                title = "Biometric unlock",
                subtitle = if (settings.biometricEnabled) "Enabled" else "Disabled",
                trailing = {
                    Switch(
                        checked = settings.biometricEnabled,
                        onCheckedChange = { viewModel.toggleBiometric(it) },
                    )
                },
            )

            SettingsItem(
                icon = Icons.Default.Pin,
                title = "PIN lock",
                subtitle = if (settings.pinEnabled) "Set" else "Not set",
                onClick = { showPinDialog = true },
            )

            SettingsItem(
                icon = Icons.Default.Timer,
                title = "Auto-lock timeout",
                subtitle = settings.autoLockTimeout.label,
                onClick = { showAutoLockPicker = true },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Data section
            SectionHeader("Data Management")

            SettingsItem(
                icon = Icons.Default.Upload,
                title = "Export data archive",
                subtitle = "Full event log as JSON",
                onClick = { viewModel.exportData() },
            )

            SettingsItem(
                icon = Icons.Default.Download,
                title = "Import data archive",
                subtitle = "Restore from JSON backup",
                onClick = { /* File picker would be wired here */ },
            )

            SettingsItem(
                icon = Icons.Default.TableChart,
                title = "Export all as CSV",
                subtitle = "All expenses across all trips",
                onClick = { viewModel.exportAllCsv() },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // About section
            SectionHeader("About")

            SettingsItem(
                icon = Icons.Default.Info,
                title = "Travel Expenses",
                subtitle = "Version 1.0.0",
            )

            SettingsItem(
                icon = Icons.Default.Description,
                title = "Licenses",
                subtitle = "Open source licenses",
            )
        }
    }

    if (showPinDialog) {
        PinSetupDialog(
            isPinSet = settings.pinEnabled,
            onDismiss = { showPinDialog = false },
            onSetPin = { pin ->
                viewModel.setPin(pin)
                showPinDialog = false
            },
            onClearPin = {
                viewModel.clearPin()
                showPinDialog = false
            },
        )
    }

    if (showAutoLockPicker) {
        AutoLockDialog(
            current = settings.autoLockTimeout,
            onDismiss = { showAutoLockPicker = false },
            onSelect = { timeout ->
                viewModel.setAutoLockTimeout(timeout)
                showAutoLockPicker = false
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
private fun PinSetupDialog(
    isPinSet: Boolean,
    onDismiss: () -> Unit,
    onSetPin: (String) -> Unit,
    onClearPin: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isPinSet) "Change PIN" else "Set PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
                    label = { Text("Enter 6-digit PIN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) confirmPin = it },
                    label = { Text("Confirm PIN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        pin.length != 6 -> error = "PIN must be 6 digits"
                        pin != confirmPin -> error = "PINs don't match"
                        else -> onSetPin(pin)
                    }
                },
            ) {
                Text("Set")
            }
        },
        dismissButton = {
            Row {
                if (isPinSet) {
                    TextButton(onClick = onClearPin) {
                        Text("Remove PIN")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}

@Composable
private fun AutoLockDialog(
    current: AutoLockTimeout,
    onDismiss: () -> Unit,
    onSelect: (AutoLockTimeout) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Auto-lock timeout") },
        text = {
            Column {
                AutoLockTimeout.entries.forEach { timeout ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(timeout) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = timeout == current,
                            onClick = { onSelect(timeout) },
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(timeout.label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
