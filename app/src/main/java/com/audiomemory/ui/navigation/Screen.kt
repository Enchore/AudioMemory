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
    /** Returns the title based on current language setting */
    fun title(language: String): String = if (language == "zh") titleZh else titleEn

    data object Home : Screen("home", "Home", "\u4e3b\u9875", Icons.Default.Home)
    data object Recording : Screen("recording", "Recording", "\u5f55\u97f3", Icons.Default.Mic)
    data object Memories : Screen("memories", "Memories", "\u8bb0\u5fc6", Icons.Default.Psychology)
    data object Speakers : Screen("speakers", "Speakers", "\u8bf4\u8bdd\u4eba", Icons.Default.People)
    data object Settings : Screen("settings", "Settings", "\u8bbe\u7f6e", Icons.Default.Settings)

    companion object {
        val bottomNavItems = listOf(Home, Memories, Speakers, Settings)
    }
}
