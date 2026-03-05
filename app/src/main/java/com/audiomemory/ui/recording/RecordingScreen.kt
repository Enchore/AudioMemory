package com.audiomemory.ui.recording

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiomemory.service.recording.AudioRecordingService
import com.audiomemory.ui.theme.*

/**
 * Full-screen recording control with live waveform visualization.
 */
@Composable
fun RecordingScreen(
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
) {
    val isRecording by AudioRecordingService.isRecording.collectAsState()
    val isPaused by AudioRecordingService.isPaused.collectAsState()
    val elapsed by AudioRecordingService.elapsedSeconds.collectAsState()

    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulse by pulseAnim.animateFloat(
        initialValue = 0.8f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "pulse"
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Status Text
        Text(
            when {
                isRecording && !isPaused -> "RECORDING"
                isPaused -> "PAUSED"
                else -> "READY"
            },
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            letterSpacing = 4.sp,
            color = when {
                isRecording && !isPaused -> Emerald
                isPaused -> Orange
                else -> Color(0xFF64748B)
            },
        )

        Spacer(Modifier.height(24.dp))

        // Waveform visualization placeholder
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(80.dp),
        ) {
            val barCount = 20
            repeat(barCount) { i ->
                val infiniteTransition = rememberInfiniteTransition(label = "bar$i")
                val height by infiniteTransition.animateFloat(
                    initialValue = 8f,
                    targetValue = if (isRecording && !isPaused) (20f + (i % 5) * 12f) else 8f,
                    animationSpec = infiniteRepeatable(
                        tween((800 + i * 50), easing = FastOutSlowInEasing),
                        RepeatMode.Reverse,
                    ),
                    label = "barHeight$i"
                )
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(height.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (isRecording && !isPaused) Emerald.copy(alpha = 0.5f + (i % 3) * 0.15f)
                            else Color(0xFF1E293B)
                        )
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        // Timer
        Text(
            formatDuration(elapsed),
            fontFamily = FontFamily.Monospace,
            fontSize = 48.sp,
            fontWeight = FontWeight.Light,
            color = if (isRecording) Emerald else Color(0xFF475569),
        )

        Spacer(Modifier.height(48.dp))

        // Control buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isRecording) {
                // Pause/Resume
                FilledIconButton(
                    onClick = { if (isPaused) onResumeRecording() else onPauseRecording() },
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Orange.copy(0.2f),
                    ),
                    shape = CircleShape,
                ) {
                    Icon(
                        if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        null, tint = Orange,
                        modifier = Modifier.size(28.dp),
                    )
                }

                // Stop
                Box(contentAlignment = Alignment.Center) {
                    if (!isPaused) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .scale(pulse)
                                .clip(CircleShape)
                                .background(Pink.copy(alpha = 0.1f))
                        )
                    }
                    FilledIconButton(
                        onClick = onStopRecording,
                        modifier = Modifier.size(64.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Pink,
                        ),
                        shape = CircleShape,
                    ) {
                        Icon(Icons.Default.Stop, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
            } else {
                // Start
                FilledIconButton(
                    onClick = onStartRecording,
                    modifier = Modifier.size(72.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Emerald,
                    ),
                    shape = CircleShape,
                ) {
                    Icon(Icons.Default.Mic, null, tint = Color.Black, modifier = Modifier.size(36.dp))
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // Info
        if (isRecording) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Processing Queue", fontSize = 12.sp, color = Color(0xFF64748B))
                    Text("Audio chunks are being processed in the background",
                        fontSize = 11.sp, color = Color(0xFF475569))
                }
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}
