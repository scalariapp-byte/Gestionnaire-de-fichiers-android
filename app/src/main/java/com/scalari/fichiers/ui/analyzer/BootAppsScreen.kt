@file:OptIn(ExperimentalMaterial3Api::class)

package com.scalari.fichiers.ui.analyzer

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.scalari.fichiers.data.BootApp
import com.scalari.fichiers.data.BootApps

@Composable
fun BootAppsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    // Recharge au retour des Réglages (l'état arrêtée/désactivée a pu changer).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val apps by produceState<List<BootApp>?>(null, refresh) { value = BootApps.list(context) }
    var showSystem by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<BootApp?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                title = {
                    Column {
                        Text("Démarrage automatique")
                        apps?.let { list ->
                            val user = list.count { !it.isSystem }
                            Text(
                                "$user appli(s) installée(s) · ${list.size - user} système",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        val list = apps
        if (list == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            BootAppsContent(
                apps = list,
                showSystem = showSystem,
                onToggleSystem = { showSystem = !showSystem },
                onSelect = { selected = it },
                modifier = Modifier.padding(padding),
            )
        }
    }

    selected?.let { app ->
        AlertDialog(
            onDismissRequest = { selected = null },
            icon = {
                app.icon?.let { Image(it.asImageBitmap(), null, Modifier.size(48.dp)) }
                    ?: Icon(Icons.Filled.Android, null)
            },
            title = { Text(app.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Cette application demande à se lancer à chaque démarrage du téléphone.")
                    Text(
                        "Android ne permet pas à une autre appli de la désactiver. Dans sa page d'infos, vous pouvez :\n" +
                            "• Forcer l'arrêt : elle ne se relancera plus au démarrage tant que vous ne l'ouvrirez pas vous-même.\n" +
                            "• Batterie → Restreinte : limite son activité en arrière-plan.\n" +
                            (if (app.isSystem) "• Désactiver (applications système)." else "• La désinstaller."),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    selected = null
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }) {
                    Icon(Icons.Filled.Info, null, Modifier.size(18.dp))
                    Text("  Infos de l'appli")
                }
            },
            dismissButton = {
                if (!app.isSystem) {
                    TextButton(onClick = {
                        selected = null
                        @Suppress("DEPRECATION")
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:${app.packageName}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }) {
                        Icon(Icons.Filled.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        Text("  Désinstaller", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
        )
    }
}

/** Liste sans état (testable en capture). */
@Composable
fun BootAppsContent(
    apps: List<BootApp>,
    showSystem: Boolean,
    onToggleSystem: () -> Unit,
    onSelect: (BootApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = if (showSystem) apps else apps.filter { !it.isSystem }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    "Touchez une appli pour l'empêcher de démarrer (forcer l'arrêt, restreindre la batterie ou la désinstaller).",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp)) {
                FilterChip(selected = showSystem, onClick = onToggleSystem, label = { Text("Afficher les applis système") })
            }
        }
        if (visible.isEmpty()) {
            item {
                Text(
                    "Aucune application installée ne se lance au démarrage.",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(visible, key = { it.packageName }) { app ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(app) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    app.icon?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize()) }
                        ?: Icon(Icons.Filled.Android, null, tint = Color(0xFF5BA33B))
                }
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (app.isSystem) "Application système" else "Application installée",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val (status, color) = when {
                    app.isDisabled -> "Désactivée" to MaterialTheme.colorScheme.outline
                    app.isStopped -> "Arrêtée" to Color(0xFF2E9E5B)
                    else -> "Au démarrage" to Color(0xFFF07A1A)
                }
                Text(
                    status,
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                    modifier = Modifier
                        .background(color.copy(alpha = 0.12f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}
