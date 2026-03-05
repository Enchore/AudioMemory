package com.audiomemory.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.audiomemory.data.entity.MemoryType
import com.audiomemory.ui.theme.*

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToRecording: () -> Unit = {},
) {
    val isRecording by viewModel.isRecording.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val elapsed by viewModel.elapsedSeconds.collectAsState()
    val memories by viewModel.recentMemories.collectAsState()
    val totalCount by viewModel.totalMemoryCount.collectAsState()
    val speakers by viewModel.speakers.collectAsState()
    val pending by viewModel.pendingChunks.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 100.dp)
    ) {
        // Recording Status Card
        item {
            RecordingStatusCard(
                isRecording = isRecording,
                isPaused = isPaused,
                elapsedSeconds = elapsed,
                pendingChunks = pending,
                onTap = onNavigateToRecording,
            )
        }

        // Stats Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = totalCount.toString(),
                    label = "Memories",
                    color = Cyan,
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = speakers.size.toString(),
                    label = "Speakers",
                    color = Purple,
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = formatDuration(elapsed),
                    label = "Session",
                    color = Orange,
                )
            }
        }

        // Recent Memories
        item {
            Text(
                "Recent Memories",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = Emerald,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (memories.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Psychology, null, tint = Color(0xFF334155), modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No memories yet", color = Color(0xFF475569), fontSize = 14.sp)
                            Text("Start recording to build your memory", color = Color(0xFF334155), fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        items(memories) { memoryWithTags ->
            val mem = memoryWithTags.memory
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
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            color = typeColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                mem.type.name,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = typeColor,
                            )
                        }
                        if (mem.isOwnerSpeech) {
                            Surface(
                                color = Emerald.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(4.dp),
                            ) {
                                Text(
                                    "OWNER",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Emerald,
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        // Importance dots
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            repeat(5) { i ->
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (i < mem.importanceScore) typeColor
                                            else Color(0xFF1E293B)
                                        )
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        mem.content,
                        fontSize = 14.sp,
                        color = DarkOnSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (memoryWithTags.tags.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            memoryWithTags.tags.take(3).forEach { tag ->
                                Surface(
                                    color = Color(0xFF1E293B),
                                    shape = RoundedCornerShape(999.dp),
                                ) {
                                    Text(
                                        "#${tag.name}",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        fontSize = 10.sp,
                                        color = Color(0xFF64748B),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingStatusCard(
    isRecording: Boolean,
    isPaused: Boolean,
    elapsedSeconds: Long,
    pendingChunks: Int,
    onTap: () -> Unit,
) {
    val pulseAnimation = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseAnimation.animateFloat(
        initialValue = 1f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "pulseScale"
    )

    Card(
        onClick = onTap,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isRecording) Color(0xFF0A1A10) else DarkSurface,
        ),
        shape = RoundedCornerShape(16.dp),
        border = if (isRecording) CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(listOf(Emerald.copy(0.3f), Cyan.copy(0.3f)))
        ) else null,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Pulsing indicator
            Box(contentAlignment = Alignment.Center) {
                if (isRecording && !isPaused) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(Emerald.copy(alpha = 0.15f))
                    )
                }
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isRecording && !isPaused -> Emerald
                                isPaused -> Orange
                                else -> Color(0xFF334155)
                            }
                        )
                )
            }

            Spacer(Modifier.width(16.dp))

            Column {
                Text(
                    text = when {
                        isRecording && !isPaused -> "Recording Active"
                        isPaused -> "Recording Paused"
                        else -> "Not Recording"
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        isRecording -> Emerald
                        isPaused -> Orange
                        else -> Color(0xFF64748B)
                    },
                )
                if (isRecording) {
                    Text(
                        formatDuration(elapsedSeconds),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                    )
                }
                if (pendingChunks > 0) {
                    Text(
                        "$pendingChunks chunks pending",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Cyan.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    color: Color,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                value,
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                label,
                fontSize = 11.sp,
                color = Color(0xFF64748B),
            )
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
