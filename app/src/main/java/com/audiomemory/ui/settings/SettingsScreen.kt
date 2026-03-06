package com.audiomemory.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.audiomemory.ui.theme.*

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val language by viewModel.language.collectAsState()
    val autoCleanup by viewModel.autoCleanup.collectAsState()
    val confidenceThreshold by viewModel.confidenceThreshold.collectAsState()

    // LLM config
    val llmBaseUrl by viewModel.llmBaseUrl.collectAsState()
    val llmModel by viewModel.llmModel.collectAsState()
    val llmApiKey by viewModel.llmApiKey.collectAsState()

    // Whisper config
    val whisperBaseUrl by viewModel.whisperBaseUrl.collectAsState()
    val whisperModel by viewModel.whisperModel.collectAsState()
    val whisperApiKey by viewModel.whisperApiKey.collectAsState()

    // Local input states
    var llmBaseUrlInput by remember(llmBaseUrl) { mutableStateOf(llmBaseUrl) }
    var llmModelInput by remember(llmModel) { mutableStateOf(llmModel) }
    var llmApiKeyInput by remember(llmApiKey) { mutableStateOf(llmApiKey) }
    var whisperBaseUrlInput by remember(whisperBaseUrl) { mutableStateOf(whisperBaseUrl) }
    var whisperModelInput by remember(whisperModel) { mutableStateOf(whisperModel) }
    var whisperApiKeyInput by remember(whisperApiKey) { mutableStateOf(whisperApiKey) }

    // Helper: pick string based on language
    val isZh = language == "zh"
    fun s(en: String, zh: String) = if (isZh) zh else en

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        Text(
            s("SETTINGS", "\u8bbe\u7f6e"),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = Color(0xFF64748B),
            letterSpacing = 2.sp,
        )

        // ========== Language Toggle ==========
        LanguageCard(language, isZh, viewModel)

        // ========== LLM API Config ==========
        Text(
            s("LLM API (Memory Extraction)", "LLM API\uff08\u8bb0\u5fc6\u63d0\u53d6\uff09"),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = Emerald,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 8.dp),
        )

        ApiConfigCard(
            baseUrlValue = llmBaseUrlInput,
            onBaseUrlChange = { llmBaseUrlInput = it },
            modelValue = llmModelInput,
            onModelChange = { llmModelInput = it },
            apiKeyValue = llmApiKeyInput,
            onApiKeyChange = { llmApiKeyInput = it },
            onSave = {
                viewModel.setLlmBaseUrl(llmBaseUrlInput)
                viewModel.setLlmModel(llmModelInput)
                viewModel.setLlmApiKey(llmApiKeyInput)
            },
            baseUrlPlaceholder = "https://api.openai.com/",
            modelPlaceholder = "gpt-4o-mini",
            isZh = isZh,
        )

        // ========== Whisper API Config ==========
        Text(
            s("Whisper API (Transcription)", "Whisper API\uff08\u8bed\u97f3\u8f6c\u5f55\uff09"),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = Cyan,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 8.dp),
        )

        ApiConfigCard(
            baseUrlValue = whisperBaseUrlInput,
            onBaseUrlChange = { whisperBaseUrlInput = it },
            modelValue = whisperModelInput,
            onModelChange = { whisperModelInput = it },
            apiKeyValue = whisperApiKeyInput,
            onApiKeyChange = { whisperApiKeyInput = it },
            onSave = {
                viewModel.setWhisperBaseUrl(whisperBaseUrlInput)
                viewModel.setWhisperModel(whisperModelInput)
                viewModel.setWhisperApiKey(whisperApiKeyInput)
            },
            baseUrlPlaceholder = "https://api.openai.com/",
            modelPlaceholder = "whisper-1",
            isZh = isZh,
        )

        // ========== Auto-cleanup toggle ==========
        AutoCleanupCard(autoCleanup, isZh, viewModel)

        // ========== Confidence Threshold ==========
        ConfidenceCard(confidenceThreshold, isZh, viewModel)

        Spacer(Modifier.height(60.dp))
    }
}

// ========== Sub-composables ==========

@Composable
private fun LanguageCard(language: String, isZh: Boolean, viewModel: SettingsViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Language / \u8bed\u8a00", fontSize = 14.sp)
                Text(
                    if (isZh) "\u754c\u9762\u8bed\u8a00" else "Interface language",
                    fontSize = 11.sp, color = Color(0xFF64748B),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = language == "zh",
                    onClick = { viewModel.setLanguage("zh") },
                    label = { Text("\u4e2d\u6587", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Emerald.copy(0.15f),
                        selectedLabelColor = Emerald,
                    ),
                    shape = RoundedCornerShape(8.dp),
                )
                FilterChip(
                    selected = language == "en",
                    onClick = { viewModel.setLanguage("en") },
                    label = { Text("EN", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Cyan.copy(0.15f),
                        selectedLabelColor = Cyan,
                    ),
                    shape = RoundedCornerShape(8.dp),
                )
            }
        }
    }
}

@Composable
private fun ApiConfigCard(
    baseUrlValue: String,
    onBaseUrlChange: (String) -> Unit,
    modelValue: String,
    onModelChange: (String) -> Unit,
    apiKeyValue: String,
    onApiKeyChange: (String) -> Unit,
    onSave: () -> Unit,
    baseUrlPlaceholder: String,
    modelPlaceholder: String,
    isZh: Boolean,
) {
    fun s(en: String, zh: String) = if (isZh) zh else en

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Base URL
            Text(s("Base URL", "\u63a5\u53e3\u5730\u5740"), fontSize = 13.sp, color = Color(0xFF94A3B8))
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = baseUrlValue,
                onValueChange = onBaseUrlChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(baseUrlPlaceholder, color = Color(0xFF334155), fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Emerald.copy(0.5f),
                    unfocusedBorderColor = Color(0xFF1E293B),
                ),
            )

            Spacer(Modifier.height(10.dp))

            // Model
            Text(s("Model", "\u6a21\u578b"), fontSize = 13.sp, color = Color(0xFF94A3B8))
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = modelValue,
                onValueChange = onModelChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(modelPlaceholder, color = Color(0xFF334155), fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Emerald.copy(0.5f),
                    unfocusedBorderColor = Color(0xFF1E293B),
                ),
            )

            Spacer(Modifier.height(10.dp))

            // API Key
            Text("API Key", fontSize = 13.sp, color = Color(0xFF94A3B8))
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = apiKeyValue,
                onValueChange = onApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("sk-...", color = Color(0xFF334155), fontSize = 13.sp) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Emerald.copy(0.5f),
                    unfocusedBorderColor = Color(0xFF1E293B),
                ),
            )

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = Emerald),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(s("Save", "\u4fdd\u5b58"), color = Color.Black)
            }
        }
    }
}

@Composable
private fun AutoCleanupCard(autoCleanup: Boolean, isZh: Boolean, viewModel: SettingsViewModel) {
    fun s(en: String, zh: String) = if (isZh) zh else en

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(s("Auto-cleanup", "\u81ea\u52a8\u6e05\u7406"), fontSize = 14.sp)
                Text(
                    s("Delete audio after processing", "\u5904\u7406\u540e\u5220\u9664\u5f55\u97f3\u6587\u4ef6"),
                    fontSize = 11.sp, color = Color(0xFF64748B),
                )
            }
            Switch(
                checked = autoCleanup,
                onCheckedChange = { viewModel.setAutoCleanup(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Emerald,
                    uncheckedTrackColor = Color(0xFF1E293B),
                ),
            )
        }
    }
}

@Composable
private fun ConfidenceCard(confidenceThreshold: Float, isZh: Boolean, viewModel: SettingsViewModel) {
    fun s(en: String, zh: String) = if (isZh) zh else en

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(s("Confidence Threshold", "\u7f6e\u4fe1\u5ea6\u9608\u503c"), fontSize = 14.sp)
                    Text(
                        s("Cloud fallback trigger", "\u4e91\u7aef\u56de\u9000\u89e6\u53d1\u503c"),
                        fontSize = 11.sp, color = Color(0xFF64748B),
                    )
                }
                Text(
                    "%.2f".format(confidenceThreshold),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = Cyan,
                )
            }
            Spacer(Modifier.height(8.dp))
            Slider(
                value = confidenceThreshold,
                onValueChange = { viewModel.setConfidenceThreshold(it) },
                valueRange = 0.5f..0.95f,
                steps = 8,
                colors = SliderDefaults.colors(
                    thumbColor = Cyan,
                    activeTrackColor = Cyan,
                    inactiveTrackColor = Color(0xFF1E293B),
                ),
            )
        }
    }
}
