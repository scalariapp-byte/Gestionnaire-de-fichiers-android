@file:OptIn(ExperimentalMaterial3Api::class)

package com.scalari.fichiers.ui.viewer

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scalari.fichiers.FileManagerApp
import com.scalari.fichiers.data.OperationManager
import com.scalari.fichiers.data.formatSize
import com.scalari.fichiers.ui.FileOpener
import com.scalari.fichiers.ui.LocalSnackbarHostState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val MAX_EDITABLE_BYTES = 2L * 1024 * 1024

data class EditorState(
    val loading: Boolean = true,
    val text: String = "",
    val original: String = "",
    val error: String? = null,
    val readOnly: Boolean = false,
    val saving: Boolean = false,
) {
    val dirty: Boolean get() = text != original
}

class TextEditorViewModel(app: Application, val file: File) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(EditorState())
    val state = _state.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    when {
                        !file.exists() -> throw IllegalStateException("Fichier introuvable")
                        file.length() > MAX_EDITABLE_BYTES ->
                            throw IllegalStateException("Fichier trop volumineux pour l'éditeur (${formatSize(file.length())}, max ${formatSize(MAX_EDITABLE_BYTES)})")
                        else -> file.readText(Charsets.UTF_8)
                    }
                }
            }
            result.fold(
                onSuccess = { txt -> _state.update { it.copy(loading = false, text = txt, original = txt, readOnly = !file.canWrite()) } },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.message) } },
            )
        }
    }

    fun onTextChange(value: String) = _state.update { it.copy(text = value) }

    fun save(then: (() -> Unit)? = null) {
        val text = _state.value.text
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    // Écriture atomique : fichier temporaire puis remplacement.
                    val tmp = File(file.parentFile, ".${file.name}.tmp")
                    tmp.writeText(text, Charsets.UTF_8)
                    if (!tmp.renameTo(file)) {
                        file.writeText(text, Charsets.UTF_8)
                        tmp.delete()
                    }
                }.isSuccess
            }
            _state.update { it.copy(saving = false, original = if (ok) text else it.original) }
            _messages.tryEmit(if (ok) "Enregistré" else "Échec de l'enregistrement")
            if (ok) {
                OperationManager.notifyChanged()
                then?.invoke()
            }
        }
    }
}

@Composable
fun TextEditorScreen(path: String, onBack: () -> Unit) {
    val vm: TextEditorViewModel = viewModel { TextEditorViewModel(FileManagerApp.instance, File(path)) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    val context = LocalContext.current
    var wrap by remember { mutableStateOf(true) }
    var askSave by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    BackHandler(enabled = state.dirty) { askSave = true }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { if (state.dirty) askSave = true else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour")
                    }
                },
                title = {
                    Column {
                        Text((if (state.dirty) "• " else "") + vm.file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (state.readOnly) Text("Lecture seule", style = MaterialTheme.typography.bodySmall)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.save() }, enabled = state.dirty && !state.saving && !state.readOnly) {
                        Icon(Icons.Filled.Save, "Enregistrer")
                    }
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Plus") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Retour à la ligne") },
                            trailingIcon = { Checkbox(checked = wrap, onCheckedChange = null) },
                            onClick = { wrap = !wrap; menu = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Ouvrir avec…") },
                            onClick = { menu = false; FileOpener.openExternal(context, vm.file, forceChooser = true) },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
        ) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> Column(
                    Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(state.error ?: "", textAlign = TextAlign.Center)
                    Button(onClick = { FileOpener.openExternal(context, vm.file, forceChooser = true) }) {
                        Text("Ouvrir avec une autre application")
                    }
                }

                else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    val minWidth = maxWidth
                    val vScroll = rememberScrollState()
                    val hScroll = rememberScrollState()
                    val textModifier = if (wrap) Modifier.fillMaxWidth()
                    else Modifier
                        .horizontalScroll(hScroll)
                        .widthIn(min = minWidth)
                    BasicTextField(
                        value = state.text,
                        onValueChange = vm::onTextChange,
                        readOnly = state.readOnly,
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(vScroll)
                            .then(textModifier)
                            .padding(12.dp),
                    )
                }
            }
        }
    }

    if (askSave) {
        AlertDialog(
            onDismissRequest = { askSave = false },
            title = { Text("Modifications non enregistrées") },
            text = { Text("Enregistrer les modifications de « ${vm.file.name} » ?") },
            confirmButton = {
                TextButton(onClick = { askSave = false; vm.save(then = onBack) }, enabled = !state.readOnly) { Text("Enregistrer") }
            },
            dismissButton = {
                TextButton(onClick = { askSave = false; onBack() }) { Text("Ignorer") }
            },
        )
    }
}
