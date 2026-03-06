package com.audiomemory.ui.speaker

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.audiomemory.data.entity.SpeakerEntity
import com.audiomemory.ui.theme.*

@Composable
fun SpeakerManagementScreen(
    viewModel: SpeakerViewModel = hiltViewModel(),
    language: String = "zh",
) {
    val speakers by viewModel.speakers.collectAsState()
    val owner = speakers.firstOrNull { it.isOwner }
    val others = speakers.filter { !it.isOwner }
    val enrollmentState by viewModel.enrollmentState.collectAsState()
    val progress by viewModel.enrollmentProgress.collectAsState()
    val secondsLeft by viewModel.enrollmentSecondsLeft.collectAsState()

    val isZh = language == "zh"
    fun s(en: String, zh: String) = if (isZh) zh else en

    // Edit speaker name dialog state
    var editingSpeaker by remember { mutableStateOf<SpeakerEntity?>(null) }
    var editName by remember { mutableStateOf("") }

    // Delete confirmation dialog state
    var deletingSpeaker by remember { mutableStateOf<SpeakerEntity?>(null) }

    // Edit name dialog
    if (editingSpeaker != null) {
        AlertDialog(
            onDismissRequest = { editingSpeaker = null },
            title = { Text(s("Rename Speaker", "\u91cd\u547d\u540d\u8bf4\u8bdd\u4eba")) },
            text = {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text(s("Name", "\u540d\u79f0")) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    editingSpeaker?.let { viewModel.updateSpeakerName(it.id, editName) }
                    editingSpeaker = null
                }) { Text(s("Save", "\u4fdd\u5b58")) }
            },
            dismissButton = {
                TextButton(onClick = { editingSpeaker = null }) { Text(s("Cancel", "\u53d6\u6d88")) }
            },
        )
    }

    // Delete confirmation dialog
    if (deletingSpeaker != null) {
        AlertDialog(
            onDismissRequest = { deletingSpeaker = null },
            title = { Text(s("Delete Speaker", "\u5220\u9664\u8bf4\u8bdd\u4eba")) },
            text = { Text(s("Delete \"${deletingSpeaker?.name}\"? This cannot be undone.",
                "\u5220\u9664 \"${deletingSpeaker?.name}\"\uff1f\u6b64\u64cd\u4f5c\u4e0d\u53ef\u64a4\u9500\u3002")) },
            confirmButton = {
                TextButton(onClick = {
                    deletingSpeaker?.let { viewModel.deleteSpeaker(it) }
                    deletingSpeaker = null
                }) { Text(s("Delete", "\u5220\u9664"), color = Pink) }
            },
            dismissButton = {
                TextButton(onClick = { deletingSpeaker = null }) { Text(s("Cancel", "\u53d6\u6d88")) }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 100.dp),
    ) {
        // Owner Section
        item {
            Text(
                s("OWNER", "\u4e3b\u4eba"),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = Emerald,
                letterSpacing = 2.sp,
            )
        }

        item {
            if (owner != null && enrollmentState is EnrollmentState.Idle) {
                OwnerCard(owner, onReEnroll = { viewModel.startOwnerEnrollment() }, language = language)
            } else if (owner != null) {
                EnrollmentCard(
                    enrollmentState = enrollmentState,
                    progress = progress,
                    secondsLeft = secondsLeft,
                    onStart = { viewModel.startOwnerEnrollment() },
                    onCancel = { viewModel.cancelEnrollment() },
                    onDismissError = { viewModel.dismissError() },
                    language = language,
                )
            } else {
                EnrollmentCard(
                    enrollmentState = enrollmentState,
                    progress = progress,
                    secondsLeft = secondsLeft,
                    onStart = { viewModel.startOwnerEnrollment() },
                    onCancel = { viewModel.cancelEnrollment() },
                    onDismissError = { viewModel.dismissError() },
                    language = language,
                )
            }
        }

        // Others Section
        if (others.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    s("OTHER SPEAKERS", "\u5176\u4ed6\u8bf4\u8bdd\u4eba"),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Cyan,
                    letterSpacing = 2.sp,
                )
            }

            items(others) { speaker ->
                SpeakerCard(speaker, onEdit = {
                    editName = speaker.name
                    editingSpeaker = speaker
                }, onDelete = {
                    deletingSpeaker = speaker
                })
            }
        }
    }
}

@Composable
private fun EnrollmentCard(
    enrollmentState: EnrollmentState,
    progress: Float,
    secondsLeft: Int,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onDismissError: () -> Unit,
    language: String = "zh",
) {
    val isZh = language == "zh"
    fun s(en: String, zh: String) = if (isZh) zh else en

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1A10)),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                listOf(Emerald.copy(0.2f), Cyan.copy(0.2f))
            )
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (enrollmentState) {
                is EnrollmentState.Idle -> {
                    Icon(Icons.Default.RecordVoiceOver, null, tint = Emerald, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(s("Register Your Voice", "\u6ce8\u518c\u4f60\u7684\u58f0\u97f3"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        s("Record 10 seconds of your voice so AudioMemory can identify you",
                            "\u5f55\u5236 10 \u79d2\u4f60\u7684\u58f0\u97f3\uff0c\u8ba9\u5e94\u7528\u80fd\u591f\u8bc6\u522b\u4f60"),
                        fontSize = 12.sp, color = Color(0xFF64748B),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        lineHeight = 18.sp, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onStart,
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.Mic, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(s("Start Enrollment", "\u5f00\u59cb\u6ce8\u518c"), color = Color.Black, fontWeight = FontWeight.SemiBold)
                    }
                }

                is EnrollmentState.Recording -> {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f, targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600), repeatMode = RepeatMode.Reverse,
                        ), label = "pulseAlpha",
                    )

                    Icon(
                        Icons.Default.Mic, null,
                        tint = Color(0xFFFF4444).copy(alpha = pulseAlpha),
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(s("Recording...", "\u5f55\u97f3\u4e2d..."), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color(0xFFFF4444))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        s("Speak naturally for ${secondsLeft}s", "\u8bf7\u81ea\u7136\u8bf4\u8bdd\uff0c\u8fd8\u5269 ${secondsLeft} \u79d2"),
                        fontSize = 13.sp, color = Color(0xFF94A3B8),
                    )
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFFFF4444),
                        trackColor = Color(0xFF1E1E2E),
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(s("Cancel", "\u53d6\u6d88"), color = Color(0xFF94A3B8))
                    }
                }

                is EnrollmentState.Processing -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        color = Emerald, strokeWidth = 3.dp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(s("Analyzing voice...", "\u5206\u6790\u58f0\u97f3\u4e2d..."), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(s("Extracting voice profile", "\u6b63\u5728\u63d0\u53d6\u58f0\u7eb9\u7279\u5f81"), fontSize = 12.sp, color = Color(0xFF64748B))
                }

                is EnrollmentState.Success -> {
                    Icon(Icons.Default.CheckCircle, null, tint = Emerald, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(s("Voice Registered!", "\u58f0\u97f3\u6ce8\u518c\u6210\u529f\uff01"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Emerald)
                }

                is EnrollmentState.Error -> {
                    Icon(Icons.Default.Error, null, tint = Pink, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(s("Enrollment Failed", "\u6ce8\u518c\u5931\u8d25"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Pink)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        enrollmentState.message, fontSize = 12.sp, color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onDismissError,
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(s("Try Again", "\u91cd\u8bd5"), color = Color.Black, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun OwnerCard(owner: SpeakerEntity, onReEnroll: () -> Unit, language: String = "zh") {
    val isZh = language == "zh"
    fun s(en: String, zh: String) = if (isZh) zh else en

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1A10)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Emerald.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Verified, null, tint = Emerald, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(s("Owner (You)", "\u4e3b\u4eba\uff08\u4f60\uff09"), fontWeight = FontWeight.SemiBold, color = Emerald)
                Text(
                    s("${owner.totalSegments} segments | Voice enrolled",
                        "${owner.totalSegments} \u4e2a\u7247\u6bb5 | \u5df2\u6ce8\u518c\u58f0\u7eb9"),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                )
            }
            IconButton(onClick = onReEnroll) {
                Icon(Icons.Default.Refresh, s("Re-enroll", "\u91cd\u65b0\u6ce8\u518c"), tint = Color(0xFF475569), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SpeakerCard(speaker: SpeakerEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    val colors = listOf(Cyan, Purple, Orange, Pink)
    val color = colors[(speaker.id % colors.size).toInt()]

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    speaker.name.take(1),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(speaker.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(
                    "${speaker.totalSegments} segments",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = Color(0xFF64748B),
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, "Edit name", tint = Color(0xFF475569), modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete", tint = Color(0xFF475569), modifier = Modifier.size(18.dp))
            }
        }
    }
}
