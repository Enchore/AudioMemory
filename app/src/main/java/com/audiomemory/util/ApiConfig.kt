package com.audiomemory.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Provides API keys and app configuration.
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
    }

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

    suspend fun loadApiKey() {
        openAiApiKey = context.dataStore.data.first()[KEY_OPENAI_API_KEY] ?: ""
    }

    suspend fun setApiKey(key: String) {
        openAiApiKey = key
        context.dataStore.edit { it[KEY_OPENAI_API_KEY] = key }
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
}
