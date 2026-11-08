package com.scalari.fichiers.ui.player

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.scalari.fichiers.data.FileType
import com.scalari.fichiers.data.FileTypes
import com.scalari.fichiers.player.PlaybackService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Connexion unique de l'interface au service de lecture. */
object PlayerConnection {
    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()
    private var future: ListenableFuture<MediaController>? = null

    fun connect(context: Context) {
        if (future != null) return
        val app = context.applicationContext
        val f = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
        future = f
        f.addListener({ runCatching { _controller.value = f.get() } }, ContextCompat.getMainExecutor(app))
    }

    /** [hiddenTitle] : titre affiché à la place du nom (notification, écran verrouillé) pour le coffre. */
    fun mediaItem(file: File, hiddenTitle: String? = null): MediaItem {
        val title = hiddenTitle ?: file.nameWithoutExtension
        return MediaItem.Builder()
            .setMediaId(file.absolutePath)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setDisplayTitle(title).setArtist(if (hiddenTitle != null) "Coffre-fort" else null).build())
            .build()
    }

    /** Arrête la lecture si le média en cours est situé sous [dir]. */
    fun stopIfUnder(dir: File) {
        val c = _controller.value ?: return
        val id = c.currentMediaItem?.mediaId ?: return
        if (id.startsWith(dir.absolutePath)) {
            c.stop()
            c.clearMediaItems()
        }
    }
}

/** Instantané de l'état du lecteur, pour l'affichage. */
data class PlayerUiState(
    val hasMedia: Boolean = false,
    val isPlaying: Boolean = false,
    val isVideo: Boolean = false,
    val title: String = "",
    val subtitle: String = "",
    val artwork: Bitmap? = null,
    val position: Long = 0,
    val duration: Long = 0,
    val index: Int = 0,
    val count: Int = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val mediaId: String = "",
)

private fun Player.snapshot(previous: PlayerUiState): PlayerUiState {
    val item = currentMediaItem
    val md = mediaMetadata
    val id = item?.mediaId.orEmpty()
    val file = File(id)
    val artBytes = md.artworkData
    val artwork = if (id == previous.mediaId && artBytes != null && previous.artwork != null) previous.artwork
    else artBytes?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() }
    return PlayerUiState(
        hasMedia = item != null,
        isPlaying = isPlaying,
        isVideo = FileTypes.of(file.name, false) == FileType.VIDEO,
        title = md.title?.toString()?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension,
        subtitle = listOfNotNull(md.artist?.toString(), md.albumTitle?.toString()).filter { it.isNotBlank() }
            .joinToString(" · ").ifEmpty { file.parentFile?.name.orEmpty() },
        artwork = artwork,
        position = currentPosition.coerceAtLeast(0),
        duration = duration.takeIf { it > 0 } ?: 0,
        index = currentMediaItemIndex,
        count = mediaItemCount,
        shuffle = shuffleModeEnabled,
        repeatMode = repeatMode,
        mediaId = id,
    )
}

@Composable
fun rememberPlayerState(player: Player?): PlayerUiState {
    var state by remember { mutableStateOf(PlayerUiState()) }
    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose {}
        state = player.snapshot(state)
        val listener = object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                state = p.snapshot(state)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    // Position de lecture rafraîchie pendant la lecture.
    LaunchedEffect(player, state.isPlaying) {
        while (player != null && state.isPlaying) {
            state = state.copy(position = player.currentPosition.coerceAtLeast(0), duration = player.duration.coerceAtLeast(0))
            delay(500)
        }
    }
    return state
}

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
