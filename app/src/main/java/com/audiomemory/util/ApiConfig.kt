package com.audiomemory.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Provides API keys and app configuration.
 * Supports configurable API providers for both LLM (chat) and Whisper (transcription).
 */
@Singleton
class ApiConfig @Inject constructor(
    private val context: Context,
) {
    companion object {
        private val KEY_OPENAI_API_KEY = stringPreferencesKey("openai_api_key")
        private val KEY_LANGUAGE = stringPreferencesKey("language")
        private val KEY_AUTO_CLEANUP = booleanPreferencesKey("auto_cleanup")
        private val KEY_CONFIDENCE_THRESHOLD = floatPreferencesKey("confidence_threshold")

        // LLM (Chat/Memory Extraction) provider config
        private val KEY_LLM_BASE_URL = stringPreferencesKey("llm_base_url")
        private val KEY_LLM_MODEL = stringPreferencesKey("llm_model")
        private val KEY_LLM_API_KEY = stringPreferencesKey("llm_api_key")

        // Whisper (Transcription) provider config
        private val KEY_WHISPER_BASE_URL = stringPreferencesKey("whisper_base_url")
        private val KEY_WHISPER_MODEL = stringPreferencesKey("whisper_model")
        private val KEY_WHISPER_API_KEY = stringPreferencesKey("whisper_api_key")

        // Defaults
        const val DEFAULT_LLM_BASE_URL = "https://api.openai.com/"
        const val DEFAULT_LLM_MODEL = "gpt-4o-mini"
        const val DEFAULT_WHISPER_BASE_URL = "https://api.openai.com/"
        const val DEFAULT_WHISPER_MODEL = "whisper-1"
    }

    // Legacy field — kept for backward compatibility, now delegates to LLM API key
    var openAiApiKey: String = ""
        private set

    val languageFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LANGUAGE] ?: "zh"
    }

    val autoCleanupFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_CLEANUP] ?: true
    }

    val confidenceThresholdFlow: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_CONFIDENCE_THRESHOLD] ?: 0.7f
    }

    // --- LLM provider flows ---
    val llmBaseUrlFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LLM_BASE_URL] ?: DEFAULT_LLM_BASE_URL
    }
    val llmModelFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LLM_MODEL] ?: DEFAULT_LLM_MODEL
    }
    val llmApiKeyFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LLM_API_KEY] ?: prefs[KEY_OPENAI_API_KEY] ?: ""
    }

    // --- Whisper provider flows ---
    val whisperBaseUrlFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_WHISPER_BASE_URL] ?: DEFAULT_WHISPER_BASE_URL
    }
    val whisperModelFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_WHISPER_MODEL] ?: DEFAULT_WHISPER_MODEL
    }
    val whisperApiKeyFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_WHISPER_API_KEY] ?: prefs[KEY_OPENAI_API_KEY] ?: ""
    }

    // Cached values for synchronous access in workers
    var llmBaseUrl: String = DEFAULT_LLM_BASE_URL
        private set
    var llmModel: String = DEFAULT_LLM_MODEL
        private set
    var llmApiKey: String = ""
        private set
    var whisperBaseUrl: String = DEFAULT_WHISPER_BASE_URL
        private set
    var whisperModel: String = DEFAULT_WHISPER_MODEL
        private set
    var whisperApiKey: String = ""
        private set

    suspend fun loadApiKey() {
        val prefs = context.dataStore.data.first()
        openAiApiKey = prefs[KEY_OPENAI_API_KEY] ?: ""
        llmBaseUrl = prefs[KEY_LLM_BASE_URL] ?: DEFAULT_LLM_BASE_URL
        llmModel = prefs[KEY_LLM_MODEL] ?: DEFAULT_LLM_MODEL
        llmApiKey = prefs[KEY_LLM_API_KEY] ?: openAiApiKey
        whisperBaseUrl = prefs[KEY_WHISPER_BASE_URL] ?: DEFAULT_WHISPER_BASE_URL
        whisperModel = prefs[KEY_WHISPER_MODEL] ?: DEFAULT_WHISPER_MODEL
        whisperApiKey = prefs[KEY_WHISPER_API_KEY] ?: openAiApiKey
    }

    /** Legacy setter — also updates LLM and Whisper API keys if they're empty */
    suspend fun setApiKey(key: String) {
        openAiApiKey = key
        context.dataStore.edit { prefs ->
            prefs[KEY_OPENAI_API_KEY] = key
            // If LLM/Whisper keys are not set independently, use this key
            if (prefs[KEY_LLM_API_KEY].isNullOrBlank()) {
                prefs[KEY_LLM_API_KEY] = key
                llmApiKey = key
            }
            if (prefs[KEY_WHISPER_API_KEY].isNullOrBlank()) {
                prefs[KEY_WHISPER_API_KEY] = key
                whisperApiKey = key
            }
        }
    }

    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { it[KEY_LANGUAGE] = lang }
    }

    suspend fun setAutoCleanup(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_CLEANUP] = enabled }
    }

    suspend fun setConfidenceThreshold(threshold: Float) {
        context.dataStore.edit { it[KEY_CONFIDENCE_THRESHOLD] = threshold }
    }

    // --- LLM provider setters ---
    suspend fun setLlmBaseUrl(url: String) {
        val normalized = if (url.endsWith("/")) url else "$url/"
        llmBaseUrl = normalized
        context.dataStore.edit { it[KEY_LLM_BASE_URL] = normalized }
    }

    suspend fun setLlmModel(model: String) {
        llmModel = model
        context.dataStore.edit { it[KEY_LLM_MODEL] = model }
    }

    suspend fun setLlmApiKey(key: String) {
        llmApiKey = key
        context.dataStore.edit { it[KEY_LLM_API_KEY] = key }
    }

    // --- Whisper provider setters ---
    suspend fun setWhisperBaseUrl(url: String) {
        val normalized = if (url.endsWith("/")) url else "$url/"
        whisperBaseUrl = normalized
        context.dataStore.edit { it[KEY_WHISPER_BASE_URL] = normalized }
    }

    suspend fun setWhisperModel(model: String) {
        whisperModel = model
        context.dataStore.edit { it[KEY_WHISPER_MODEL] = model }
    }

    suspend fun setWhisperApiKey(key: String) {
        whisperApiKey = key
        context.dataStore.edit { it[KEY_WHISPER_API_KEY] = key }
    }
}
