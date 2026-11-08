package com.scalari.fichiers.ui.player

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.io.File
import java.util.Locale

/** Moteur d'Android (Media3), via le service de lecture. */
class ExoVideoEngine(private val player: Player) : VideoEngine {
    override val engineName = "Android"
    override val mediaId: String get() = player.currentMediaItem?.mediaId.orEmpty()
    override val title: String
        get() = player.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() } ?: File(mediaId).nameWithoutExtension
    override val isPlaying: Boolean get() = player.isPlaying
    override val position: Long get() = player.currentPosition.coerceAtLeast(0)
    override val duration: Long get() = player.duration.coerceAtLeast(0)
    override val speed: Float get() = player.playbackParameters.speed
    override val videoWidth: Int get() = player.videoSize.width
    override val videoHeight: Int get() = player.videoSize.height
    override val hasNext: Boolean get() = player.hasNextMediaItem()
    override val hasPrev: Boolean get() = player.hasPreviousMediaItem()

    override fun play() = player.play()
    override fun pause() = player.pause()
    override fun seekTo(ms: Long) = player.seekTo(ms)
    override fun setSpeed(speed: Float) = player.setPlaybackSpeed(speed)
    override fun next() = player.seekToNextMediaItem()
    override fun prev() = player.seekToPreviousMediaItem()

    /**
     * Vrai si la vidéo en cours ne peut pas être décodée par Android : piste vidéo présente
     * mais non prise en charge, ou erreur de décodage.
     */
    fun cannotDecode(error: PlaybackException?): Boolean {
        if (error != null && error.errorCode in listOf(
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            )
        ) return true
        val video = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
        return video.isNotEmpty() && video.none { g -> (0 until g.length).any { g.isTrackSupported(it) } }
    }

    private data class Entry(val group: Tracks.Group, val index: Int, val option: TrackOption)

    private fun entries(type: Int): List<Entry> {
        var n = 0
        return player.currentTracks.groups.filter { it.type == type }.flatMap { g ->
            (0 until g.length).filter { g.isTrackSupported(it) }.map { i ->
                n++
                val f = g.getTrackFormat(i)
                val lang = f.language?.takeIf { it != "und" }?.let { code ->
                    Locale.forLanguageTag(code).getDisplayLanguage(Locale.FRENCH).replaceFirstChar { it.uppercase() }
                }
                val extra = if (type == C.TRACK_TYPE_AUDIO && f.channelCount > 0) {
                    if (f.channelCount >= 6) "5.1" else if (f.channelCount == 2) "stéréo" else "${f.channelCount} canaux"
                } else null
                val label = listOfNotNull(f.label ?: lang ?: "Piste $n", extra).joinToString(" · ")
                Entry(g, i, TrackOption("$type:${g.hashCode()}:$i", label, g.isTrackSelected(i)))
            }
        }
    }

    override fun audioTracks() = entries(C.TRACK_TYPE_AUDIO).map { it.option }
    override fun textTracks() = entries(C.TRACK_TYPE_TEXT).map { it.option }

    private fun select(type: Int, track: TrackOption) {
        val e = entries(type).firstOrNull { it.option.id == track.id } ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(e.group.mediaTrackGroup, e.index))
            .build()
    }

    override fun selectAudio(track: TrackOption) = select(C.TRACK_TYPE_AUDIO, track)

    override fun selectText(track: TrackOption?) {
        if (track == null) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        } else {
            select(C.TRACK_TYPE_TEXT, track)
        }
    }

    @OptIn(UnstableApi::class)
    @Composable
    override fun VideoSurface(fit: FitMode, modifier: Modifier) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    useController = false
                    keepScreenOn = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    this.player = this@ExoVideoEngine.player
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
            modifier = modifier,
        )
    }
}
