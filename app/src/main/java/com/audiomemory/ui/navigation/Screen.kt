package com.audiomemory.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Navigation destinations for the app.
 */
sealed class Screen(
    val route: String,
    val titleEn: String,
    val titleZh: String,
    val icon: ImageVector,
) {
    data object Home : Screen("home", "Home", "主页", Icons.Default.Home)
    data object Recording : Screen("recording", "Recording", "录音", Icons.Default.Mic)
    data object Memories : Screen("memories", "Memories", "记忆", Icons.Default.Psychology)
    data object Speakers : Screen("speakers", "Speakers", "说话人", Icons.Default.People)
    data object Settings : Screen("settings", "Settings", "设置", Icons.Default.Settings)

    companion object {
        val bottomNavItems = listOf(Home, Memories, Speakers, Settings)
    }
}
