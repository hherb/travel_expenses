package com.travelexpenses.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelexpenses.android.auth.PinManager
import com.travelexpenses.export.CsvExporter
import com.travelexpenses.sync.EventArchive
import com.travelexpenses.sync.EventArchiveSerializer
import com.travelexpenses.sync.SyncManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsState(
    val biometricEnabled: Boolean = false,
    val pinEnabled: Boolean = false,
    val autoLockTimeout: AutoLockTimeout = AutoLockTimeout.IMMEDIATE,
)

enum class AutoLockTimeout(val label: String, val seconds: Int) {
    IMMEDIATE("Immediate", 0),
    ONE_MINUTE("1 minute", 60),
    FIVE_MINUTES("5 minutes", 300),
    FIFTEEN_MINUTES("15 minutes", 900),
}

class SettingsViewModel(
    private val syncManager: SyncManager,
    private val csvExporter: CsvExporter,
    private val pinManager: PinManager,
    private val defaultCategories: com.travelexpenses.validation.DefaultCategories,
) : ViewModel() {

    private val _settings = MutableStateFlow(SettingsState())
    val settings: StateFlow<SettingsState> = _settings

    private val _exportArchive = MutableSharedFlow<String>()
    val exportArchive: SharedFlow<String> = _exportArchive

    private val _exportCsv = MutableSharedFlow<String>()
    val exportCsv: SharedFlow<String> = _exportCsv

    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message

    init {
        loadSettings()
    }

    private fun loadSettings() {
        _settings.update {
            it.copy(
                pinEnabled = pinManager.isPinSet(),
            )
        }
    }

    fun toggleBiometric(enabled: Boolean) {
        _settings.update { it.copy(biometricEnabled = enabled) }
    }

    fun setAutoLockTimeout(timeout: AutoLockTimeout) {
        _settings.update { it.copy(autoLockTimeout = timeout) }
    }

    fun setPin(pin: String) {
        viewModelScope.launch {
            pinManager.setPin(pin)
            _settings.update { it.copy(pinEnabled = true) }
            _message.emit("PIN set successfully")
        }
    }

    fun clearPin() {
        pinManager.clearPin()
        _settings.update { it.copy(pinEnabled = false) }
    }

    fun exportData() {
        viewModelScope.launch {
            val archive = syncManager.exportArchive()
            val json = EventArchiveSerializer.serialize(archive)
            _exportArchive.emit(json)
        }
    }

    fun importData(json: String) {
        viewModelScope.launch {
            try {
                val archive = EventArchiveSerializer.deserialize(json)
                syncManager.importArchive(archive)
                _message.emit("Data imported successfully (${archive.eventCount} events)")
            } catch (e: Exception) {
                _message.emit("Import failed: ${e.message}")
            }
        }
    }

    fun exportAllCsv() {
        viewModelScope.launch {
            val csv = csvExporter.exportAll()
            _exportCsv.emit(csv)
        }
    }
}
