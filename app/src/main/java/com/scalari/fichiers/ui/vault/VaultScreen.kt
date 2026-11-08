@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.scalari.fichiers.ui.vault

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scalari.fichiers.data.FileType
import com.scalari.fichiers.data.OperationManager
import com.scalari.fichiers.data.formatDate
import com.scalari.fichiers.data.formatSize
import com.scalari.fichiers.data.vault.VaultBiometric
import com.scalari.fichiers.data.vault.VaultEntry
import com.scalari.fichiers.data.vault.VaultManager
import com.scalari.fichiers.data.vault.VaultState
import com.scalari.fichiers.ui.AppNavigator
import com.scalari.fichiers.ui.FileOpener
import com.scalari.fichiers.ui.LocalSnackbarHostState
import com.scalari.fichiers.ui.components.ConfirmDialog
import com.scalari.fichiers.ui.components.icon
import com.scalari.fichiers.ui.components.tint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Empêche captures d'écran et aperçu dans les applis récentes tant que l'écran est affiché. */
private var secureCount = 0

@Composable
fun SecureWindow() {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        // Compteur : pendant une transition, deux écrans sécurisés coexistent.
        if (secureCount++ == 0) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            if (--secureCount == 0) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

@Composable
fun VaultScreen(navigator: AppNavigator, isTab: Boolean = false) {
    SecureWindow()
    val context = LocalContext.current
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { VaultManager.refreshState() } }
    val state by VaultManager.state.collectAsStateWithLifecycle()

    when (val s = state) {
        VaultState.NotSetUp -> SetupContent(onBack = if (isTab) null else navigator::back)
        VaultState.Locked -> LockedContent(onBack = if (isTab) null else navigator::back)
        is VaultState.Unlocked -> UnlockedContent(s.entries, navigator, isTab) {
            VaultManager.lock(context)
            if (!isTab) navigator.back()
        }
    }
}

@Composable
private fun CenteredScaffold(title: String, onBack: (() -> Unit)?, content: @Composable () -> Unit) {
    val snackbar = LocalSnackbarHostState.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) { content() }
    }
}

@Composable
private fun SetupContent(onBack: (() -> Unit)?) {
    var step by remember { mutableIntStateOf(0) } // 0 intro, 1 code, 2 confirmation
    var first by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var reset by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    CenteredScaffold("Coffre-fort", onBack) {
        when (step) {
            0 -> Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(Icons.Filled.Shield, null, Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Coffre-fort chiffré", style = MaterialTheme.typography.headlineSmall)
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("• Les fichiers sont chiffrés en AES-256 et retirés de leur emplacement : ils disparaissent de la galerie et des autres applis.")
                        Text("• Les noms de fichiers sont chiffrés eux aussi.")
                        Text("• Déverrouillage par code, et par empreinte si vous l'activez.")
                        Text("• Le coffre survit à la désinstallation : réinstallez l'appli et saisissez votre code.")
                        Text(
                            "• Code oublié = fichiers perdus. Il n'existe aucune récupération possible.",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) { Text("Créer mon code") }
            }

            1 -> PinPad(
                title = "Choisissez un code",
                subtitle = "4 chiffres minimum. 6 ou plus est nettement plus sûr.",
                error = error, busy = false, resetKey = reset,
            ) { pin ->
                error = VaultManager.validatePin(pin)
                if (error == null) {
                    first = pin
                    step = 2
                    reset++
                }
            }

            else -> PinPad(
                title = "Confirmez le code",
                subtitle = "Saisissez-le une seconde fois.",
                error = error, busy = busy, resetKey = reset,
            ) { pin ->
                if (pin != first) {
                    error = "Les codes ne correspondent pas"
                    step = 1
                    reset++
                } else {
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { VaultManager.setup(pin) } }
                        busy = false
                        result.exceptionOrNull()?.let { error = it.message; step = 1; reset++ }
                    }
                }
            }
        }
    }
}

@Composable
private fun LockedContent(onBack: (() -> Unit)?) {
    val context = LocalContext.current
    val activity = context.findActivity() as? FragmentActivity
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var reset by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val bioEnabled = remember { VaultBiometric.isEnabled(context) && VaultBiometric.isAvailable(context) }

    fun biometric() {
        if (activity == null) return
        VaultBiometric.unlock(activity, onKey = { key ->
            scope.launch {
                busy = true
                val r = withContext(Dispatchers.IO) { runCatching { VaultManager.unlockWithKeyBytes(key) } }
                busy = false
                r.exceptionOrNull()?.let { error = "Coffre illisible : ${it.message}" }
            }
        }, onError = { error = it })
    }

    LaunchedEffect(Unit) { if (bioEnabled) biometric() }

    CenteredScaffold("Coffre-fort", onBack) {
        PinPad(
            title = "Coffre verrouillé",
            subtitle = "Saisissez votre code",
            error = error, busy = busy, resetKey = reset,
            onBiometric = if (bioEnabled) ::biometric else null,
        ) { pin ->
            val wait = VaultManager.lockoutRemaining()
            if (wait > 0) {
                error = "Trop d'essais. Réessayez dans ${(wait + 999) / 1000} s"
                reset++
                return@PinPad
            }
            busy = true
            scope.launch {
                val ok = withContext(Dispatchers.IO) { runCatching { VaultManager.unlock(pin) }.getOrDefault(false) }
                busy = false
                if (!ok) {
                    error = "Code incorrect"
                    reset++
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun UnlockedContent(entries: List<VaultEntry>, navigator: AppNavigator, isTab: Boolean, onLockAndExit: () -> Unit) {
    val context = LocalContext.current
    val activity = context.findActivity() as? FragmentActivity
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var confirmDelete by remember { mutableStateOf<List<VaultEntry>?>(null) }
    var changePin by remember { mutableStateOf(false) }
    var opening by remember { mutableStateOf(false) }
    var bioEnabled by remember { mutableStateOf(VaultBiometric.isEnabled(context)) }
    val selectionMode = selected.isNotEmpty()

    // Fichiers envoyés depuis l'explorateur pendant que le coffre était verrouillé.
    LaunchedEffect(Unit) {
        val pending = VaultManager.takePending()
        if (pending.isNotEmpty()) OperationManager.vaultAdd(pending)
    }
    BackHandler(enabled = selectionMode || !isTab) { if (selectionMode) selected = emptySet() else onLockAndExit() }

    fun message(text: String) { scope.launch { snackbar.showSnackbar(text) } }

    fun open(entry: VaultEntry) {
        opening = true
        scope.launch {
            val file = withContext(Dispatchers.IO) { runCatching { VaultManager.openTemp(context, entry) } }
            opening = false
            file.fold(
                onSuccess = { f ->
                    if (entry.type == FileType.IMAGE) {
                        navigator.image(f.absolutePath, secure = true)
                    } else if (entry.type == FileType.VIDEO || entry.type == FileType.AUDIO) {
                        navigator.player(f.absolutePath, secure = true)
                    } else {
                        VaultManager.externalOpen = true
                        if (!FileOpener.openExternal(context, f)) {
                            VaultManager.externalOpen = false
                            message("Aucune application pour ouvrir ce fichier")
                        }
                    }
                },
                onFailure = { message("Déchiffrement impossible : ${it.message}") },
            )
        }
    }

    fun selectedEntries() = entries.filter { it.id in selected }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    navigationIcon = { IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Filled.Close, "Annuler") } },
                    title = { Text("${selected.size} sélectionné(s)") },
                    actions = {
                        IconButton(onClick = {
                            OperationManager.vaultRestore(selectedEntries())
                            selected = emptySet()
                        }) { Icon(Icons.Filled.RestoreFromTrash, "Restaurer") }
                        IconButton(onClick = { confirmDelete = selectedEntries() }) { Icon(Icons.Filled.Delete, "Supprimer") }
                        IconButton(onClick = { selected = entries.mapTo(HashSet()) { it.id } }) {
                            Icon(Icons.Filled.SelectAll, "Tout sélectionner")
                        }
                    },
                )
            } else {
                TopAppBar(
                    navigationIcon = {
                        if (!isTab) IconButton(onClick = onLockAndExit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                    },
                    title = {
                        Column {
                            Text("Coffre-fort")
                            Text(
                                "${entries.size} fichier(s) · ${formatSize(entries.sumOf { it.size })}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onLockAndExit) { Icon(Icons.Filled.Lock, "Verrouiller") }
                        var menu by remember { mutableStateOf(false) }
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Plus") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (VaultBiometric.isAvailable(context)) {
                                DropdownMenuItem(
                                    text = { Text(if (bioEnabled) "Désactiver l'empreinte" else "Activer l'empreinte") },
                                    leadingIcon = { Icon(Icons.Filled.Fingerprint, null) },
                                    onClick = {
                                        menu = false
                                        if (bioEnabled) {
                                            VaultBiometric.disable(context)
                                            bioEnabled = false
                                            message("Déverrouillage biométrique désactivé")
                                        } else {
                                            val key = VaultManager.currentKeyBytes()
                                            if (activity != null && key != null) {
                                                VaultBiometric.enable(activity, key) { ok, err ->
                                                    bioEnabled = ok
                                                    message(if (ok) "Empreinte activée" else "Échec : ${err ?: "annulé"}")
                                                }
                                            }
                                        }
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Changer le code") },
                                leadingIcon = { Icon(Icons.Filled.LockReset, null) },
                                onClick = { menu = false; changePin = true },
                            )
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (entries.isEmpty()) {
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(Icons.Filled.Shield, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.outline)
                    Text(
                        "Le coffre est vide.\nDans l'explorateur, appuyez longuement sur des fichiers puis menu ⋮ → « Mettre au coffre-fort ».",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { navigator.browser(null) }) { Text("Ouvrir l'explorateur") }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(entries, key = { it.id }) { entry ->
                        val isSel = entry.id in selected
                        ListItem(
                            modifier = Modifier
                                .animateItem()
                                .combinedClickable(
                                    onClick = {
                                        if (selectionMode) selected = if (isSel) selected - entry.id else selected + entry.id
                                        else open(entry)
                                    },
                                    onLongClick = { selected = if (isSel) selected - entry.id else selected + entry.id },
                                ),
                            colors = ListItemDefaults.colors(
                                containerColor = if (isSel) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                            ),
                            leadingContent = {
                                val thumb by produceState<android.graphics.Bitmap?>(null, entry.id) {
                                    value = withContext(Dispatchers.IO) { VaultManager.thumbnail(context, entry) }
                                }
                                Box(
                                    Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(entry.type.tint().copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val t = thumb
                                    if (t != null) {
                                        androidx.compose.foundation.Image(
                                            t.asImageBitmap(), null,
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    } else {
                                        Icon(entry.type.icon(), null, tint = entry.type.tint())
                                    }
                                    if (isSel) {
                                        Icon(
                                            Icons.Filled.CheckCircle, null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .size(20.dp)
                                                .background(MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.CircleShape),
                                        )
                                    }
                                }
                            },
                            headlineContent = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text("${formatSize(entry.size)} · ajouté le ${formatDate(entry.addedAt)}") },
                        )
                    }
                }
            }
            if (opening) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
        }
    }

    confirmDelete?.let { list ->
        ConfirmDialog(
            title = "Supprimer définitivement ?",
            text = "${list.size} fichier(s) seront effacés du coffre. Ils ne passent pas par la corbeille : action irréversible.",
            confirmLabel = "Supprimer",
            destructive = true,
            onDismiss = { confirmDelete = null },
            onConfirm = {
                confirmDelete = null
                selected = emptySet()
                OperationManager.vaultDelete(list)
            },
        )
    }
    if (changePin) ChangePinDialog(onDismiss = { changePin = false }, onDone = { changePin = false; message("Code modifié") })
}

@Composable
private fun ChangePinDialog(onDismiss: () -> Unit, onDone: () -> Unit) {
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val numeric = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Changer le code") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = old, onValueChange = { old = it.filter(Char::isDigit) }, label = { Text("Code actuel") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = numeric,
                )
                OutlinedTextField(
                    value = new, onValueChange = { new = it.filter(Char::isDigit) }, label = { Text("Nouveau code") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = numeric,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && old.isNotEmpty() && new.isNotEmpty(), onClick = {
                busy = true
                scope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { VaultManager.changePin(old, new) } }
                    busy = false
                    r.fold(onSuccess = { onDone() }, onFailure = { error = it.message })
                }
            }) { Text(if (busy) "…" else "Valider") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Annuler") } },
    )
}
