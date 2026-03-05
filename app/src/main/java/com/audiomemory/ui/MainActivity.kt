package com.audiomemory.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.*
import com.audiomemory.service.recording.AudioRecordingService
import com.audiomemory.ui.home.HomeScreen
import com.audiomemory.ui.memory.MemoryBrowserScreen
import com.audiomemory.ui.navigation.Screen
import com.audiomemory.ui.recording.RecordingScreen
import com.audiomemory.ui.settings.SettingsScreen
import com.audiomemory.ui.speaker.SpeakerManagementScreen
import com.audiomemory.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions handled */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissions()

        setContent {
            AudioMemoryTheme {
                AudioMemoryApp()
            }
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    @Composable
    private fun AudioMemoryApp() {
        val navController = rememberNavController()
        val currentRoute by navController.currentBackStackEntryAsState()
        val currentDestination = currentRoute?.destination?.route

        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = DarkSurface,
                    contentColor = Color(0xFF64748B),
                    tonalElevation = 0.dp,
                ) {
                    Screen.bottomNavItems.forEach { screen ->
                        val selected = currentDestination == screen.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (currentDestination != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(Screen.Home.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    screen.icon, null,
                                    tint = if (selected) Emerald else Color(0xFF475569),
                                )
                            },
                            label = {
                                Text(
                                    screen.titleEn,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (selected) Emerald else Color(0xFF475569),
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Emerald.copy(alpha = 0.1f),
                            ),
                        )
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(padding),
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        onNavigateToRecording = { navController.navigate(Screen.Recording.route) }
                    )
                }
                composable(Screen.Recording.route) {
                    RecordingScreen(
                        onStartRecording = { startRecordingService() },
                        onStopRecording = { stopRecordingService() },
                        onPauseRecording = { pauseRecordingService() },
                        onResumeRecording = { resumeRecordingService() },
                    )
                }
                composable(Screen.Memories.route) {
                    MemoryBrowserScreen()
                }
                composable(Screen.Speakers.route) {
                    SpeakerManagementScreen()
                }
                composable(Screen.Settings.route) {
                    SettingsScreen()
                }
            }
        }
    }

    private fun startRecordingService() {
        val intent = Intent(this, AudioRecordingService::class.java).apply {
            action = AudioRecordingService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopRecordingService() {
        val intent = Intent(this, AudioRecordingService::class.java).apply {
            action = AudioRecordingService.ACTION_STOP
        }
        startService(intent)
    }

    private fun pauseRecordingService() {
        val intent = Intent(this, AudioRecordingService::class.java).apply {
            action = AudioRecordingService.ACTION_PAUSE
        }
        startService(intent)
    }

    private fun resumeRecordingService() {
        val intent = Intent(this, AudioRecordingService::class.java).apply {
            action = AudioRecordingService.ACTION_RESUME
        }
        startService(intent)
    }
}
