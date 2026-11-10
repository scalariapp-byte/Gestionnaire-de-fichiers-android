package com.scalari.fichiers.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.scalari.fichiers.data.FileOps
import com.scalari.fichiers.data.OperationProgress
import com.scalari.fichiers.data.SortBy
import com.scalari.fichiers.data.formatDate
import com.scalari.fichiers.data.formatSize
import com.scalari.fichiers.data.mimeTypeOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun NameInputDialog(
    title: String,
    initial: String = "",
    confirmLabel: String = "OK",
    selectBaseName: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val baseEnd = initial.lastIndexOf('.').takeIf { selectBaseName && it > 0 } ?: initial.length
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, baseEnd))) }
    val error = if (value.text.isEmpty()) null else FileOps.validateName(value.text)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val submit = { if (value.text.isNotBlank() && error == null) onConfirm(value.text.trim()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                isError = error != null,
                supportingText = { if (error != null) Text(error) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = value.text.isNotBlank() && error == null) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    destructive: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (destructive) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                else ButtonDefaults.textButtonColors(),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
fun SortDialog(
    sortBy: SortBy,
    ascending: Boolean,
    foldersFirst: Boolean,
    /** Nom du dossier courant : affiche l'option « Seulement pour ce dossier ». */
    folderName: String? = null,
    folderOnly: Boolean = false,
    onDismiss: () -> Unit,
    onApply: (SortBy, Boolean, Boolean, Boolean) -> Unit,
) {
    var by by remember { mutableStateOf(sortBy) }
    var asc by remember { mutableStateOf(ascending) }
    var folders by remember { mutableStateOf(foldersFirst) }
    var onlyHere by remember { mutableStateOf(folderOnly) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trier par") },
        text = {
            Column {
                SortBy.entries.forEach { option ->
                    RadioRow(option.label, by == option) { by = option }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                RadioRow("Croissant", asc) { asc = true }
                RadioRow("Décroissant", !asc) { asc = false }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { folders = !folders },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = folders, onCheckedChange = { folders = it })
                    Text("Dossiers en premier")
                }
                if (folderName != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onlyHere = !onlyHere },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = onlyHere, onCheckedChange = { onlyHere = it })
                        Text("Seulement pour « $folderName »", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(by, asc, folders, onlyHere) }) { Text("Appliquer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

@Composable
fun PropertiesDialog(files: List<File>, onDismiss: () -> Unit) {
    val stats by produceState<FileOps.DirStats?>(initialValue = null, files) {
        value = withContext(Dispatchers.IO) { FileOps.stats(files) }
    }
    val single = files.singleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (single != null) "Propriétés" else "${files.size} éléments") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (single != null) {
                    Property("Nom", single.name)
                    Property("Emplacement", single.parent ?: "/")
                    Property("Type", if (single.isDirectory) "Dossier" else mimeTypeOf(single))
                    Property("Modifié le", formatDate(single.lastModified()))
                } else {
                    Property("Emplacement", files.first().parent ?: "/")
                }
                val s = stats
                if (s == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.padding(end = 12.dp), strokeWidth = 2.dp)
                        Text("Calcul de la taille…")
                    }
                } else {
                    Property("Taille", "${formatSize(s.bytes)} (${"%,d".format(s.bytes)} octets)")
                    if (single == null || single.isDirectory) {
                        Property("Contenu", "${s.files} fichier(s), ${s.folders} dossier(s)")
                    }
                }
                if (single != null) {
                    val flags = buildList {
                        if (single.canRead()) add("lecture")
                        if (single.canWrite()) add("écriture")
                        if (single.name.startsWith(".")) add("caché")
                    }
                    Property("Accès", flags.joinToString(", ").ifEmpty { "aucun" })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

@Composable
private fun Property(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun OperationProgressDialog(progress: OperationProgress, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(progress.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    progress.currentFile.ifEmpty { "Préparation…" },
                    maxLines = 1,
                    fontWeight = FontWeight.Medium,
                )
                val f = progress.fraction
                if (f != null) {
                    LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth())
                    val detail = buildString {
                        if (progress.totalBytes > 0) append("${formatSize(progress.processedBytes)} / ${formatSize(progress.totalBytes)}")
                        if (progress.totalItems > 1) {
                            if (isNotEmpty()) append(" · ")
                            append("${progress.processedItems} / ${progress.totalItems}")
                        }
                    }
                    Text(detail, style = MaterialTheme.typography.bodySmall)
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { TextButton(onClick = onCancel) { Text("Annuler") } },
    )
}
