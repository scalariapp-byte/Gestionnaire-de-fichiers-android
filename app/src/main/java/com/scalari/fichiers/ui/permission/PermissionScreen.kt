package com.scalari.fichiers.ui.permission

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scalari.fichiers.data.StoragePermission

@Composable
fun PermissionScreen(onGranted: () -> Unit) {
    val context = LocalContext.current
    val legacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onGranted()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.FolderOpen,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(96.dp),
        )
        Text("Accès aux fichiers", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (StoragePermission.needsAllFilesAccess())
                "Pour parcourir, copier, déplacer et supprimer vos fichiers, Android exige l'autorisation " +
                    "« Accès à tous les fichiers ». Activez-la sur l'écran suivant, puis revenez dans l'application."
            else
                "Pour parcourir et gérer vos fichiers, l'application a besoin de l'autorisation d'accès au stockage.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = {
            if (StoragePermission.needsAllFilesAccess()) StoragePermission.openAllFilesSettings(context)
            else legacyLauncher.launch(StoragePermission.legacyPermissions)
        }) {
            Text("Autoriser l'accès")
        }
        Text(
            "Aucune donnée ne quitte votre appareil : l'application ne demande pas l'accès à Internet.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
