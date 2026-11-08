@file:OptIn(ExperimentalFoundationApi::class)

package com.scalari.fichiers.ui.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn as AndroidOptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.scalari.fichiers.ui.vault.findActivity
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

/** Format d'image : Ajuster (bandes noires), Remplir (rogne), Étirer. */
enum class FitMode(val label: String) { FIT("Ajuster"), ZOOM("Remplir"), STRETCH("Étirer") }

private enum class Gesture { NONE, SEEK, BRIGHTNESS, VOLUME }

/** Message affiché au centre pendant un geste (luminosité, volume, avance). */
data class Hud(val icon: ImageVector, val text: String, val fraction: Float? = null)

private val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

@AndroidOptIn(markerClass = [UnstableApi::class])
@Composable
fun VideoPlayer(player: Player, title: String, onBack: () -> Unit, resumeFrom: Long = 0L, allowPip: Boolean = true) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context.findActivity()
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    // ---------- État du lecteur
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var position by remember { mutableLongStateOf(player.currentPosition) }
    var duration by remember { mutableLongStateOf(player.duration.coerceAtLeast(0)) }
    var videoSize by remember { mutableStateOf(player.videoSize) }
    var tracks by remember { mutableStateOf(player.currentTracks) }
    var speed by remember { mutableFloatStateOf(player.playbackParameters.speed) }
    var hasNext by remember { mutableStateOf(player.hasNextMediaItem()) }
    var hasPrev by remember { mutableStateOf(player.hasPreviousMediaItem()) }
    var currentTitle by remember { mutableStateOf(title) }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                isPlaying = p.isPlaying
                duration = p.duration.coerceAtLeast(0)
                videoSize = p.videoSize
                tracks = p.currentTracks
                speed = p.playbackParameters.speed
                hasNext = p.hasNextMediaItem()
                hasPrev = p.hasPreviousMediaItem()
                p.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() }?.let { currentTitle = it }
                position = p.currentPosition
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            position = player.currentPosition
            delay(250)
        }
    }

    // ---------- Reprise : mémorise la position régulièrement et en quittant
    LaunchedEffect(player) {
        while (true) {
            delay(5_000)
            player.currentMediaItem?.mediaId?.let { ResumeStore.save(context, it, player.currentPosition, player.duration) }
        }
    }
    var resumeBanner by remember { mutableStateOf(resumeFrom > 0) }
    LaunchedEffect(resumeBanner) { if (resumeBanner) { delay(6_000); resumeBanner = false } }

    // ---------- Interface
    var controls by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var showUnlock by remember { mutableStateOf(false) }
    var fit by remember { mutableStateOf(FitMode.FIT) }
    var hud by remember { mutableStateOf<Hud?>(null) }
    var interaction by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<String?>(null) } // "speed" | "audio" | "text"
    var inPip by remember { mutableStateOf(activity?.isInPictureInPictureMode == true) }
    var brightness by remember {
        mutableFloatStateOf(
            activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0 }
                ?: runCatching {
                    android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f
                }.getOrDefault(0.5f)
        )
    }

    // Masquage automatique des commandes pendant la lecture.
    LaunchedEffect(controls, isPlaying, interaction, dialog) {
        if (controls && isPlaying && dialog == null) {
            delay(3_500)
            controls = false
        }
    }
    LaunchedEffect(showUnlock) { if (showUnlock) { delay(2_500); showUnlock = false } }
    LaunchedEffect(hud) { if (hud != null) { delay(900); hud = null } }

    // ---------- Fenêtre : plein écran immersif, luminosité, orientation
    DisposableEffect(Unit) {
        val window = activity?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, view) }
        insets?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            insets?.show(WindowInsetsCompat.Type.systemBars())
            window?.let { w ->
                w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            player.currentMediaItem?.mediaId?.let { ResumeStore.save(context, it, player.currentPosition, player.duration) }
            if (activity?.isChangingConfigurations != true) player.pause()
        }
    }
    // Vidéo plus large que haute : passage automatique en paysage (une fois par vidéo).
    var autoRotated by remember { mutableStateOf<VideoSize?>(null) }
    LaunchedEffect(videoSize) {
        if (videoSize.width > 0 && autoRotated != videoSize) {
            autoRotated = videoSize
            activity?.requestedOrientation = if (videoSize.width > videoSize.height) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // ---------- Image dans l'image
    fun pipParams(auto: Boolean): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val w = videoSize.width.takeIf { it > 0 } ?: 16
        val h = videoSize.height.takeIf { it > 0 } ?: 9
        val ratio = (w.toFloat() / h).coerceIn(1f / 2.39f, 2.39f)
        val b = PictureInPictureParams.Builder().setAspectRatio(Rational((ratio * 1000).toInt(), 1000))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) b.setAutoEnterEnabled(auto).setSeamlessResizeEnabled(true)
        return b.build()
    }
    LaunchedEffect(isPlaying, videoSize) {
        // Android 12+ : passe en fenêtre flottante en quittant l'appli pendant la lecture.
        if (allowPip && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) pipParams(isPlaying)?.let { runCatching { activity?.setPictureInPictureParams(it) } }
    }
    DisposableEffect(activity) {
        val ca = activity as? ComponentActivity
        val listener = Consumer<PictureInPictureModeChangedInfo> { inPip = it.isInPictureInPictureMode }
        ca?.addOnPictureInPictureModeChangedListener(listener)
        onDispose {
            ca?.removeOnPictureInPictureModeChangedListener(listener)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) pipParams(false)?.let { runCatching { activity?.setPictureInPictureParams(it) } }
        }
    }
    // Fenêtre flottante fermée (croix) ou appli quittée hors fenêtre flottante : pause.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (activity?.isInPictureInPictureMode != true) player.pause() }

    fun showControls() { controls = true; interaction++ }
    fun seekBy(ms: Long) {
        val target = (player.currentPosition + ms).coerceIn(0, player.duration.coerceAtLeast(0))
        player.seekTo(target)
        position = target
        hud = Hud(if (ms > 0) Icons.Filled.FastForward else Icons.Filled.FastRewind, "${if (ms > 0) "+" else "−"}${abs(ms) / 1000} s")
    }
    fun setBrightness(v: Float) {
        brightness = v.coerceIn(0.01f, 1f)
        activity?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = brightness } }
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
                    useController = false
                    keepScreenOn = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    this.player = player
                }
            },
            update = {
                it.player = player
                it.resizeMode = when (fit) {
                    FitMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    FitMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    FitMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                }
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )

        if (!inPip) {
            // ---------- Gestes
            var gesture by remember { mutableStateOf(Gesture.NONE) }
            var acc by remember { mutableStateOf(Offset.Zero) }
            var startX by remember { mutableFloatStateOf(0f) }
            var seekStart by remember { mutableLongStateOf(0L) }
            var seekTarget by remember { mutableLongStateOf(0L) }
            var brightStart by remember { mutableFloatStateOf(0f) }
            var volStart by remember { mutableIntStateOf(0) }
            val maxVol = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(locked) {
                        detectTapGestures(
                            onTap = {
                                if (locked) showUnlock = true
                                else if (controls) controls = false else showControls()
                            },
                            onDoubleTap = { o ->
                                if (locked) return@detectTapGestures
                                when {
                                    o.x < size.width / 3f -> seekBy(-10_000)
                                    o.x > size.width * 2f / 3f -> seekBy(10_000)
                                    else -> if (player.isPlaying) player.pause() else player.play()
                                }
                            },
                        )
                    }
                    .pointerInput(locked) {
                        if (locked) return@pointerInput
                        detectDragGestures(
                            onDragStart = { start ->
                                gesture = Gesture.NONE
                                acc = Offset.Zero
                                startX = start.x
                                seekStart = player.currentPosition
                                seekTarget = seekStart
                                brightStart = brightness
                                volStart = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                            },
                            onDragEnd = {
                                if (gesture == Gesture.SEEK) { player.seekTo(seekTarget); position = seekTarget }
                                gesture = Gesture.NONE
                            },
                            onDragCancel = { gesture = Gesture.NONE },
                            onDrag = { change, delta ->
                                change.consume()
                                acc += delta
                                if (gesture == Gesture.NONE) {
                                    val slop = 24f
                                    gesture = when {
                                        abs(acc.x) > slop && abs(acc.x) > abs(acc.y) -> Gesture.SEEK
                                        abs(acc.y) > slop -> if (startX < size.width / 2f) Gesture.BRIGHTNESS else Gesture.VOLUME
                                        else -> Gesture.NONE
                                    }
                                }
                                when (gesture) {
                                    Gesture.SEEK -> {
                                        // Toute la largeur de l'écran = 90 secondes.
                                        val d = (acc.x / size.width * 90_000).toLong()
                                        seekTarget = (seekStart + d).coerceIn(0, player.duration.coerceAtLeast(0))
                                        val diff = seekTarget - seekStart
                                        hud = Hud(
                                            if (diff >= 0) Icons.Filled.FastForward else Icons.Filled.FastRewind,
                                            "${if (diff >= 0) "+" else "−"}${formatDuration(abs(diff))}  ·  ${formatDuration(seekTarget)}",
                                        )
                                    }
                                    Gesture.BRIGHTNESS -> {
                                        setBrightness(brightStart - acc.y / (size.height * 0.75f))
                                        hud = Hud(Icons.Filled.BrightnessMedium, "Luminosité ${(brightness * 100).toInt()} %", brightness)
                                    }
                                    Gesture.VOLUME -> {
                                        val v = (volStart - acc.y / (size.height * 0.75f) * maxVol).toInt().coerceIn(0, maxVol)
                                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
                                        hud = Hud(Icons.Filled.VolumeUp, "Volume $v / $maxVol", v.toFloat() / maxVol)
                                    }
                                    Gesture.NONE -> Unit
                                }
                            },
                        )
                    }
            )

            VideoControls(
                visible = controls && !locked,
                title = currentTitle,
                isPlaying = isPlaying,
                position = position,
                duration = duration,
                speed = speed,
                fit = fit,
                hasPrev = hasPrev,
                hasNext = hasNext,
                hasSubtitles = tracks.groups.any { it.type == C.TRACK_TYPE_TEXT },
                hasAudioChoice = trackEntries(tracks, C.TRACK_TYPE_AUDIO).size > 1,
                showPip = allowPip,
                onBack = onBack,
                onPlayPause = { showControls(); if (player.isPlaying) player.pause() else player.play() },
                onSeek = { showControls(); player.seekTo(it); position = it },
                onRewind = { showControls(); seekBy(-10_000) },
                onForward = { showControls(); seekBy(10_000) },
                onPrev = { showControls(); player.seekToPreviousMediaItem() },
                onNext = { showControls(); player.seekToNextMediaItem() },
                onSpeed = { dialog = "speed" },
                onSubtitles = { dialog = "text" },
                onAudio = { dialog = "audio" },
                onFit = { showControls(); fit = FitMode.entries[(fit.ordinal + 1) % FitMode.entries.size]; hud = Hud(Icons.Filled.AspectRatio, fit.label) },
                onRotate = {
                    showControls()
                    val landscape = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    activity?.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                },
                onLock = { locked = true; controls = false; showUnlock = true },
                onPip = { pipParams(false)?.let { runCatching { activity?.enterPictureInPictureMode(it) } } },
            )

            // Verrouillage : seul le cadenas reste accessible.
            AnimatedVisibility(locked && showUnlock, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.CenterStart)) {
                Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.55f), modifier = Modifier.padding(24.dp)) {
                    IconButton(onClick = { locked = false; showControls() }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Filled.LockOpen, "Déverrouiller", tint = Color.White)
                    }
                }
            }

            hud?.let { HudPill(it, Modifier.align(Alignment.Center)) }

            AnimatedVisibility(resumeBanner, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier
                        .safeDrawingPadding()
                        .padding(bottom = 96.dp),
                ) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Reprise à ${formatDuration(resumeFrom)}", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = {
                            resumeBanner = false
                            player.seekTo(0)
                            player.currentMediaItem?.mediaId?.let { ResumeStore.clear(context, it) }
                        }) { Text("Depuis le début") }
                    }
                }
            }
        }
    }

    // ---------- Dialogues : vitesse, pistes audio, sous-titres
    when (dialog) {
        "speed" -> ChoiceDialog(
            title = "Vitesse de lecture",
            options = speeds.map { (if (it == 1f) "Normale" else "×" + it.toString().trimEnd('0').trimEnd('.').replace('.', ',')) to (it == speed) },
            onDismiss = { dialog = null },
            onPick = { i -> player.setPlaybackSpeed(speeds[i]); dialog = null },
        )
        "audio", "text" -> {
            val type = if (dialog == "audio") C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT
            val entries = trackEntries(tracks, type)
            val textOff = type == C.TRACK_TYPE_TEXT && entries.none { it.selected }
            ChoiceDialog(
                title = if (type == C.TRACK_TYPE_AUDIO) "Piste audio" else "Sous-titres",
                options = (if (type == C.TRACK_TYPE_TEXT) listOf("Désactivés" to textOff) else emptyList()) +
                    entries.map { it.label to it.selected },
                onDismiss = { dialog = null },
                onPick = { i ->
                    val params = player.trackSelectionParameters.buildUpon()
                    if (type == C.TRACK_TYPE_TEXT && i == 0) {
                        params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    } else {
                        val e = entries[if (type == C.TRACK_TYPE_TEXT) i - 1 else i]
                        params.setTrackTypeDisabled(type, false)
                            .setOverrideForType(TrackSelectionOverride(e.group.mediaTrackGroup, e.index))
                    }
                    player.trackSelectionParameters = params.build()
                    dialog = null
                },
            )
        }
    }
}

private data class TrackEntry(val group: Tracks.Group, val index: Int, val label: String, val selected: Boolean)

private fun trackEntries(tracks: Tracks, type: Int): List<TrackEntry> {
    var n = 0
    return tracks.groups.filter { it.type == type }.flatMap { g ->
        (0 until g.length).filter { g.isTrackSupported(it) }.map { i ->
            n++
            val f = g.getTrackFormat(i)
            val lang = f.language?.takeIf { it != "und" }?.let { code ->
                Locale.forLanguageTag(code).getDisplayLanguage(Locale.FRENCH).replaceFirstChar { it.uppercase() }
            }
            val extra = buildList {
                if (type == C.TRACK_TYPE_AUDIO && f.channelCount > 0) add(if (f.channelCount >= 6) "5.1" else if (f.channelCount == 2) "stéréo" else "${f.channelCount} canaux")
            }
            val label = listOfNotNull(f.label ?: lang ?: "Piste $n", extra.joinToString().ifEmpty { null }).joinToString(" · ")
            TrackEntry(g, i, label, g.isTrackSelected(i))
        }
    }
}

@Composable
private fun ChoiceDialog(title: String, options: List<Pair<String, Boolean>>, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (options.isEmpty()) Text("Aucune piste disponible")
                options.forEachIndexed { i, (label, selected) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(i) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, modifier = Modifier.weight(1f), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                        if (selected) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

@Composable
private fun HudPill(hud: Hud, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.large, color = Color.Black.copy(alpha = 0.6f), modifier = modifier) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(hud.icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
                Text(hud.text, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 10.dp))
            }
            hud.fraction?.let { f ->
                Spacer(Modifier.height(10.dp))
                Box(Modifier.width(160.dp).height(4.dp).background(Color.White.copy(alpha = 0.25f), CircleShape)) {
                    Box(Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).height(4.dp).background(Color.White, CircleShape))
                }
            }
        }
    }
}

/** Commandes du lecteur, sans état (testables en capture). */
@Composable
fun VideoControls(
    visible: Boolean,
    title: String,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    speed: Float,
    fit: FitMode,
    hasPrev: Boolean,
    hasNext: Boolean,
    hasSubtitles: Boolean,
    hasAudioChoice: Boolean,
    showPip: Boolean = true,
    onBack: () -> Unit = {},
    onPlayPause: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onRewind: () -> Unit = {},
    onForward: () -> Unit = {},
    onPrev: () -> Unit = {},
    onNext: () -> Unit = {},
    onSpeed: () -> Unit = {},
    onSubtitles: () -> Unit = {},
    onAudio: () -> Unit = {},
    onFit: () -> Unit = {},
    onRotate: () -> Unit = {},
    onLock: () -> Unit = {},
    onPip: () -> Unit = {},
) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize()) {
            // Dégradés haut et bas pour la lisibilité.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
            ) {
                // Barre du haut
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = Color.White) }
                    Text(
                        title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .basicMarquee(),
                    )
                    TextButton(onClick = onSpeed) {
                        Icon(Icons.Filled.Speed, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Text(
                            " ×" + speed.toString().trimEnd('0').trimEnd('.').replace('.', ','),
                            color = Color.White,
                        )
                    }
                    if (hasAudioChoice) IconButton(onClick = onAudio) { Icon(Icons.Filled.Audiotrack, "Piste audio", tint = Color.White) }
                    if (hasSubtitles) IconButton(onClick = onSubtitles) { Icon(Icons.Filled.ClosedCaption, "Sous-titres", tint = Color.White) }
                    if (showPip && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        IconButton(onClick = onPip) { Icon(Icons.Filled.PictureInPictureAlt, "Fenêtre flottante", tint = Color.White) }
                    }
                }

                Spacer(Modifier.weight(1f))
                // Centre
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundButton(Icons.Filled.SkipPrevious, "Précédente", 48, enabled = hasPrev, onClick = onPrev)
                    RoundButton(Icons.Filled.Replay10, "Reculer de 10 s", 52, onClick = onRewind)
                    RoundButton(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (isPlaying) "Pause" else "Lecture", 76, filled = true, onClick = onPlayPause)
                    RoundButton(Icons.Filled.Forward10, "Avancer de 10 s", 52, onClick = onForward)
                    RoundButton(Icons.Filled.SkipNext, "Suivante", 48, enabled = hasNext, onClick = onNext)
                }
                Spacer(Modifier.weight(1f))

                // Bas : progression et outils
                Column(Modifier.padding(horizontal = 16.dp)) {
                    var dragging by remember { mutableStateOf(false) }
                    var dragValue by remember { mutableFloatStateOf(0f) }
                    val d = duration.coerceAtLeast(1)
                    Slider(
                        value = if (dragging) dragValue else (position.toFloat() / d).coerceIn(0f, 1f),
                        onValueChange = { dragging = true; dragValue = it },
                        onValueChangeFinished = { onSeek((dragValue * d).toLong()); dragging = false },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        ),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${formatDuration(if (dragging) (dragValue * d).toLong() else position)} / ${formatDuration(duration)}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onLock) { Icon(Icons.Filled.Lock, "Verrouiller l'écran", tint = Color.White) }
                        TextButton(onClick = onFit) {
                            Icon(Icons.Filled.AspectRatio, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Text(" ${fit.label}", color = Color.White)
                        }
                        IconButton(onClick = onRotate) { Icon(Icons.Filled.ScreenRotation, "Pivoter", tint = Color.White) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, description: String, size: Int, enabled: Boolean = true, filled: Boolean = false, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = if (filled) Color.White.copy(alpha = 0.95f) else Color.Black.copy(alpha = 0.35f),
        modifier = Modifier.size(size.dp),
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxSize()) {
            Icon(
                icon, description,
                tint = when {
                    filled -> Color.Black
                    enabled -> Color.White
                    else -> Color.White.copy(alpha = 0.35f)
                },
                modifier = Modifier.size((size * 0.55f).dp),
            )
        }
    }
}

