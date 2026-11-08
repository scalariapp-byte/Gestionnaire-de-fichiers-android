package com.scalari.fichiers.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

enum class Tab(val route: String, val label: String, val selectedIcon: ImageVector, val icon: ImageVector) {
    HOME(Routes.HOME, "Accueil", Icons.Filled.Home, Icons.Outlined.Home),
    FILES(Routes.TAB_FILES, "Fichiers", Icons.Filled.Folder, Icons.Outlined.Folder),
    RECENT(Routes.TAB_RECENT, "Récents", Icons.Filled.History, Icons.Outlined.History),
    VAULT(Routes.VAULT, "Coffre", Icons.Filled.Shield, Icons.Outlined.Shield),
}

/** Onglet correspondant à une route ; l'explorateur ouvert depuis l'accueil compte comme « Fichiers ». */
fun tabForRoute(route: String?): Tab? = when (route) {
    Routes.HOME -> Tab.HOME
    Routes.TAB_FILES, Routes.BROWSER -> Tab.FILES
    Routes.TAB_RECENT -> Tab.RECENT
    Routes.VAULT -> Tab.VAULT
    else -> null
}

@Composable
fun AppBottomBar(current: Tab?, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Tab.entries.forEach { tab ->
            val selected = tab == current
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab) },
                icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}
