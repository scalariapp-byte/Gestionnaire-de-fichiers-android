package com.scalari.fichiers.ui.player

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File

/**
 * Moteur VLC : décode presque tous les formats vidéo et audio en logiciel (WMV, MPEG-2,
 * vieux DivX…), et affiche les sous-titres ASS avec leurs styles.
 */
class VlcVideoEngine(context: Context, private val playlist: List<File>, startIndex: Int, startPosition: Long) : VideoEngine {
    private val libVlc = LibVLC(context.applicationContext, arrayListOf("--audio-time-stretch", "--sub-autodetect-file"))
    private val mp = MediaPlayer(libVlc)
    private var index = startIndex.coerceIn(0, (playlist.size - 1).coerceAtLeast(0))
    private var pendingSeek = startPosition
    private var ended = false

    init {
        mp.setEventListener { e ->
            when (e.type) {
                MediaPlayer.Event.Playing -> {
                    ended = false
                    if (pendingSeek > 0) {
                        mp.setTime(pendingSeek)
                        pendingSeek = 0
                    }
                }
                MediaPlayer.Event.EndReached -> {
                    ended = true
                    if (hasNext) next()
                }
            }
        }
        load()
    }

    private fun load() {
        val file = playlist.getOrNull(index) ?: return
        val media = Media(libVlc, Uri.fromFile(file))
        mp.setMedia(media)
        media.release()
        mp.play()
    }

    override val engineName = "VLC"
    override val mediaId: String get() = playlist.getOrNull(index)?.absolutePath.orEmpty()
    override val title: String get() = playlist.getOrNull(index)?.nameWithoutExtension.orEmpty()
    override val isPlaying: Boolean get() = mp.isPlaying
    override val position: Long get() = mp.getTime().coerceAtLeast(0)
    override val duration: Long get() = mp.getLength().coerceAtLeast(0)
    override val speed: Float get() = mp.getRate()
    override val videoWidth: Int get() = mp.getCurrentVideoTrack()?.width ?: 0
    override val videoHeight: Int get() = mp.getCurrentVideoTrack()?.height ?: 0
    override val hasNext: Boolean get() = index < playlist.size - 1
    override val hasPrev: Boolean get() = index > 0

    override fun play() {
        if (ended) { ended = false; load() } else mp.play()
    }
    override fun pause() = mp.pause()
    override fun seekTo(ms: Long) { mp.setTime(ms.coerceAtLeast(0)) }
    override fun setSpeed(speed: Float) { mp.setRate(speed) }
    override fun next() { if (hasNext) { index++; load() } }
    override fun prev() {
        if (position > 3_000 || !hasPrev) seekTo(0) else { index--; load() }
    }

    override fun audioTracks(): List<TrackOption> {
        val current = mp.getAudioTrack()
        return mp.getAudioTracks().orEmpty().filter { it.id >= 0 }
            .map { TrackOption(it.id.toString(), it.name ?: "Piste ${it.id}", it.id == current) }
    }

    override fun textTracks(): List<TrackOption> {
        val current = mp.getSpuTrack()
        return mp.getSpuTracks().orEmpty().filter { it.id >= 0 }
            .map { TrackOption(it.id.toString(), it.name ?: "Sous-titres ${it.id}", it.id == current) }
    }

    override fun selectAudio(track: TrackOption) { track.id.toIntOrNull()?.let { mp.setAudioTrack(it) } }
    override fun selectText(track: TrackOption?) { mp.setSpuTrack(track?.id?.toIntOrNull() ?: -1) }

    @Composable
    override fun VideoSurface(fit: FitMode, modifier: Modifier) {
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    keepScreenOn = true
                    mp.attachViews(this, null, true, false)
                }
            },
            update = {
                mp.setVideoScale(
                    when (fit) {
                        FitMode.FIT -> MediaPlayer.ScaleType.SURFACE_BEST_FIT
                        FitMode.ZOOM -> MediaPlayer.ScaleType.SURFACE_FIT_SCREEN
                        FitMode.STRETCH -> MediaPlayer.ScaleType.SURFACE_FILL
                    }
                )
            },
            onRelease = { mp.detachViews() },
            modifier = modifier,
        )
    }

    fun release() {
        runCatching { mp.setEventListener(null) }
        runCatching { mp.stop() }
        runCatching { mp.detachViews() }
        runCatching { mp.release() }
        runCatching { libVlc.release() }
    }
}
