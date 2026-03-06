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

    // LLM provider config
    val llmBaseUrl: StateFlow<String> =
        apiConfig.llmBaseUrlFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ApiConfig.DEFAULT_LLM_BASE_URL)

    val llmModel: StateFlow<String> =
        apiConfig.llmModelFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ApiConfig.DEFAULT_LLM_MODEL)

    val llmApiKey: StateFlow<String> =
        apiConfig.llmApiKeyFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    // Whisper provider config
    val whisperBaseUrl: StateFlow<String> =
        apiConfig.whisperBaseUrlFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ApiConfig.DEFAULT_WHISPER_BASE_URL)

    val whisperModel: StateFlow<String> =
        apiConfig.whisperModelFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ApiConfig.DEFAULT_WHISPER_MODEL)

    val whisperApiKey: StateFlow<String> =
        apiConfig.whisperApiKeyFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

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

    // LLM provider setters
    fun setLlmBaseUrl(url: String) {
        viewModelScope.launch { apiConfig.setLlmBaseUrl(url) }
    }

    fun setLlmModel(model: String) {
        viewModelScope.launch { apiConfig.setLlmModel(model) }
    }

    fun setLlmApiKey(key: String) {
        viewModelScope.launch { apiConfig.setLlmApiKey(key) }
    }

    // Whisper provider setters
    fun setWhisperBaseUrl(url: String) {
        viewModelScope.launch { apiConfig.setWhisperBaseUrl(url) }
    }

    fun setWhisperModel(model: String) {
        viewModelScope.launch { apiConfig.setWhisperModel(model) }
    }

    fun setWhisperApiKey(key: String) {
        viewModelScope.launch { apiConfig.setWhisperApiKey(key) }
    }
}
