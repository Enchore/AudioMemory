package com.audiomemory.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audiomemory.util.ApiConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val apiConfig: ApiConfig,
) : ViewModel() {

    val language: StateFlow<String> =
        apiConfig.languageFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "zh")

    val autoCleanup: StateFlow<Boolean> =
        apiConfig.autoCleanupFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val confidenceThreshold: StateFlow<Float> =
        apiConfig.confidenceThresholdFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.7f)

    fun setLanguage(lang: String) {
        viewModelScope.launch { apiConfig.setLanguage(lang) }
    }

    fun setApiKey(key: String) {
        viewModelScope.launch { apiConfig.setApiKey(key) }
    }

    fun setAutoCleanup(enabled: Boolean) {
        viewModelScope.launch { apiConfig.setAutoCleanup(enabled) }
    }

    fun setConfidenceThreshold(threshold: Float) {
        viewModelScope.launch { apiConfig.setConfidenceThreshold(threshold) }
    }
}
