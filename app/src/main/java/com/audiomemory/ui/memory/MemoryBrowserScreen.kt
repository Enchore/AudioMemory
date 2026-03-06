package com.audiomemory.ui.memory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.audiomemory.data.entity.MemoryType
import com.audiomemory.data.entity.MemoryWithTags
import com.audiomemory.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryBrowserScreen(
    viewModel: MemoryBrowserViewModel = hiltViewModel(),
    language: String = "zh",
) {
    val memories by viewModel.memories.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()

    val isZh = language == "zh"
    fun s(en: String, zh: String) = if (isZh) zh else en

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = viewModel::onSearchQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(s("Search memories...", "\u641c\u7d22\u8bb0\u5fc6..."), color = Color(0xFF475569)) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFF475569)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Emerald.copy(0.5f),
                unfocusedBorderColor = Color(0xFF1E293B),
                focusedContainerColor = DarkSurface,
                unfocusedContainerColor = DarkSurface,
            ),
        )

        Spacer(Modifier.height(12.dp))

        // Filter chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChipItem(s("All", "\u5168\u90e8"), selectedFilter == null, Emerald) { viewModel.onFilterChanged(null) }
            MemoryType.entries.forEach { type ->
                val color = when (type) {
                    MemoryType.FACT -> Cyan
                    MemoryType.DECISION -> Purple
                    MemoryType.ACTION -> Orange
                    MemoryType.INSIGHT -> Emerald
                    MemoryType.PREFERENCE -> Pink
                }
                FilterChipItem(type.name, selectedFilter == type, color) { viewModel.onFilterChanged(type) }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Memory list
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 100.dp),
        ) {
            items(memories) { memoryWithTags ->
                MemoryListItem(memoryWithTags, language)
            }
        }
    }
}

@Composable
private fun FilterChipItem(
    label: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color.copy(alpha = 0.15f),
            selectedLabelColor = color,
            containerColor = Color(0xFF0D0D20),
            labelColor = Color(0xFF64748B),
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = if (selected) color.copy(0.3f) else Color(0xFF1E293B),
            selectedBorderColor = color.copy(0.3f),
            enabled = true,
            selected = selected,
        ),
        shape = RoundedCornerShape(999.dp),
    )
}

@Composable
private fun MemoryListItem(memoryWithTags: MemoryWithTags, language: String = "zh") {
    val mem = memoryWithTags.memory
    val isZh = language == "zh"
    fun s(en: String, zh: String) = if (isZh) zh else en

    val typeColor = when (mem.type) {
        MemoryType.FACT -> Cyan
        MemoryType.DECISION -> Purple
        MemoryType.ACTION -> Orange
        MemoryType.INSIGHT -> Emerald
        MemoryType.PREFERENCE -> Pink
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            // Color indicator bar
            Surface(
                modifier = Modifier.width(3.dp).height(48.dp),
                color = typeColor,
                shape = RoundedCornerShape(2.dp),
            ) {}

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        color = typeColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            mem.type.name,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = typeColor,
                        )
                    }
                    if (mem.isOwnerSpeech) {
                        Text(s("Owner", "\u4e3b\u4eba"), fontSize = 10.sp, color = Emerald.copy(0.7f))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    mem.content,
                    fontSize = 13.sp,
                    color = DarkOnSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (memoryWithTags.tags.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        memoryWithTags.tags.take(3).forEach { tag ->
                            Text("#${tag.name}", fontSize = 9.sp, color = Color(0xFF475569))
                        }
                    }
                }
            }
        }
    }
}
