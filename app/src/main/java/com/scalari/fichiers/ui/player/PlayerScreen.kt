@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.scalari.fichiers.ui.player

import android.Manifest
import android.os.Build
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.scalari.fichiers.FileManagerApp
import com.scalari.fichiers.data.FileType
import com.scalari.fichiers.data.FileTypes
import com.scalari.fichiers.data.NaturalOrder
import com.scalari.fichiers.ui.vault.findActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Lit [path]. La liste de lecture contient les fichiers du même type (audio ou vidéo) du dossier,
 * dans l'ordre naturel des noms. Si [path] est vide, affiche simplement la lecture en cours.
 */
@Composable
fun PlayerScreen(path: String, onBack: () -> Unit, secure: Boolean = false) {
    if (secure) com.scalari.fichiers.ui.vault.SecureWindow()
    val context = LocalContext.current
    LaunchedEffect(Unit) { PlayerConnection.connect(context) }
    val controller by PlayerConnection.controller.collectAsStateWithLifecycle()
    val state = rememberPlayerState(controller)
    val requestedType = remember(path) { if (path.isEmpty()) null else FileTypes.of(File(path).name, false) }
    var started by rememberSaveable(path) { mutableStateOf(path.isEmpty()) }

    // Notifications (Android 13+) : nécessaires pour les commandes de lecture en arrière-plan.
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(requestedType) {
        if (!secure && requestedType == FileType.AUDIO && Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(controller, path) {
        val c = controller ?: return@LaunchedEffect
        if (started) return@LaunchedEffect
        val file = File(path)
        val showHidden = FileManagerApp.instance.settingsState.value.showHidden
        val playlist = if (secure) listOf(file) else withContext(Dispatchers.IO) {
            file.parentFile?.listFiles()
                ?.filter { it.isFile && FileTypes.of(it.name, false) == requestedType && (showHidden || !it.name.startsWith(".")) }
                ?.sortedWith(compareBy<File, String>(NaturalOrder) { it.name })
                ?: emptyList()
        }.ifEmpty { listOf(file) }
        val start = playlist.indexOfFirst { it.absolutePath == file.absolutePath }.coerceAtLeast(0)
        c.setMediaItems(playlist.map { PlayerConnection.mediaItem(it, if (secure) "Contenu protégé" else null) }, start, 0L)
        c.prepare()
        c.play()
        started = true
    }

    val isVideo = requestedType == FileType.VIDEO || (requestedType == null && state.isVideo)
    if (controller == null) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (isVideo) {
        VideoPlayer(controller!!, state.title, onBack)
    } else {
        AudioPlayerContent(
            state = state,
            onBack = onBack,
            onPlayPause = { controller?.let { if (it.isPlaying) it.pause() else it.play() } },
            onPrevious = { controller?.seekToPrevious() },
            onNext = { controller?.seekToNext() },
            onSeek = { controller?.seekTo(it) },
            onShuffle = { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } },
            onRepeat = {
                controller?.let {
                    it.repeatMode = when (it.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                }
            },
        )
    }
}

@Composable
private fun VideoPlayer(player: Player, title: String, onBack: () -> Unit) {
    val view = LocalView.current
    val activity = LocalContext.current.findActivity()
    // Plein écran immersif ; la vidéo se met en pause en quittant l'écran (pas lors d'une rotation).
    DisposableEffect(Unit) {
        val window = activity?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, view) }
        insets?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            insets?.show(WindowInsetsCompat.Type.systemBars())
            if (activity?.isChangingConfigurations != true) player.pause()
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    keepScreenOn = true
                    this.player = player
                }
            },
            update = { it.player = player },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )
        Row(
            Modifier
                .statusBarsPadding()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = Color.Black.copy(alpha = 0.45f)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = Color.White)
                }
            }
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .basicMarquee(),
            )
        }
    }
}

/** Interface du lecteur audio, sans état (testable en capture). */
@Composable
fun AudioPlayerContent(
    state: PlayerUiState,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(scheme.primaryContainer.copy(alpha = 0.55f), scheme.surface, scheme.surface)))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.padding(start = 0.dp)) {
                    Icon(Icons.Filled.KeyboardArrowDown, "Réduire", modifier = Modifier.size(30.dp))
                }
                Text(
                    "Lecture en cours",
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (state.count > 0) "${state.index + 1} / ${state.count}" else "",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(min = 48.dp).padding(end = 8.dp),
                    textAlign = TextAlign.End,
                )
            }

            Spacer(Modifier.weight(0.6f))
            Box(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp)
                    .aspectRatio(1f)
                    .shadow(24.dp, MaterialTheme.shapes.extraLarge)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(Brush.linearGradient(listOf(Color(0xFF4F8BFF), Color(0xFF8E4EC6)))),
                contentAlignment = Alignment.Center,
            ) {
                val art = state.artwork
                if (art != null) {
                    Image(art.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Filled.MusicNote, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(120.dp))
                }
            }
            Spacer(Modifier.weight(0.5f))

            Text(
                state.title.ifEmpty { "—" },
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(),
                textAlign = TextAlign.Center,
            )
            Text(
                state.subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(20.dp))

            var dragging by remember { mutableStateOf(false) }
            var dragValue by remember { mutableFloatStateOf(0f) }
            val duration = state.duration.coerceAtLeast(1)
            Slider(
                value = if (dragging) dragValue else (state.position.toFloat() / duration).coerceIn(0f, 1f),
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = { onSeek((dragValue * duration).toLong()); dragging = false },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(
                    formatDuration(if (dragging) (dragValue * duration).toLong() else state.position),
                    style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(formatDuration(state.duration), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onShuffle) {
                    Icon(Icons.Filled.Shuffle, "Aléatoire", tint = if (state.shuffle) scheme.primary else scheme.onSurfaceVariant)
                }
                IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.SkipPrevious, "Précédent", modifier = Modifier.size(36.dp))
                }
                FilledIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(80.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = scheme.primary),
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (state.isPlaying) "Pause" else "Lecture",
                        modifier = Modifier.size(40.dp),
                    )
                }
                IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.SkipNext, "Suivant", modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = onRepeat) {
                    Icon(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        "Répéter",
                        tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) scheme.primary else scheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.weight(0.4f))
        }
    }
}
