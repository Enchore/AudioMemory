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
    var apiKeyInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        Text(
            "SETTINGS",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = Color(0xFF64748B),
            letterSpacing = 2.sp,
        )

        // Language Toggle
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
                    Text("Language / 语言", fontSize = 14.sp)
                    Text("Interface language", fontSize = 11.sp, color = Color(0xFF64748B))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = language == "zh",
                        onClick = { viewModel.setLanguage("zh") },
                        label = { Text("中文", fontSize = 12.sp) },
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

        // API Key
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("OpenAI API Key", fontSize = 14.sp)
                Text("Required for Whisper + GPT cloud features", fontSize = 11.sp, color = Color(0xFF64748B))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("sk-...", color = Color(0xFF334155)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Emerald.copy(0.5f),
                        unfocusedBorderColor = Color(0xFF1E293B),
                    ),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.setApiKey(apiKeyInput) },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Save", color = Color.Black)
                }
            }
        }

        // Auto-cleanup toggle
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
                    Text("Auto-cleanup", fontSize = 14.sp)
                    Text("Delete audio after processing", fontSize = 11.sp, color = Color(0xFF64748B))
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

        // Confidence Threshold
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
                        Text("Confidence Threshold", fontSize = 14.sp)
                        Text("Cloud fallback trigger", fontSize = 11.sp, color = Color(0xFF64748B))
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

        Spacer(Modifier.height(60.dp))
    }
}
