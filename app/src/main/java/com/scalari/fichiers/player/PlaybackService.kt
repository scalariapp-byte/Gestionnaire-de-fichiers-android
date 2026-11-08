package com.scalari.fichiers.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.scalari.fichiers.MainActivity
import java.io.File

/**
 * Service de lecture : garde le lecteur vivant hors de l'écran (musique en arrière-plan),
 * publie la notification multimédia et les commandes de l'écran verrouillé / casque.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        // Décodeurs du téléphone en priorité ; FFmpeg prend le relais pour les formats qu'il ne lit pas.
        val renderers = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
        val player = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pause si le casque est débranché
            .build()

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(object : MediaSession.Callback {
                // Les éléments reçus d'un contrôleur perdent leur URI : on la reconstruit
                // depuis l'identifiant, qui est le chemin du fichier.
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
                    mediaItems.map { item ->
                        val file = File(item.mediaId)
                        item.buildUpon()
                            .setUri(Uri.fromFile(file))
                            .setSubtitleConfigurations(externalSubtitles(file))
                            .build()
                    }.toMutableList()
                )
            })
            .build()
    }

    /** Sous-titres posés à côté de la vidéo avec le même nom (film.srt, film.fr.srt, film.vtt…). */
    private fun externalSubtitles(video: File): List<MediaItem.SubtitleConfiguration> {
        val dir = video.parentFile ?: return emptyList()
        val base = video.nameWithoutExtension
        val files = dir.listFiles { f ->
            f.isFile && f.name.startsWith(base) && f.extension.lowercase() in setOf("srt", "vtt", "ass", "ssa")
        }.orEmpty().sortedBy { it.name.length }
        return files.mapIndexed { i, f ->
            val mime = when (f.extension.lowercase()) {
                "srt" -> MimeTypes.APPLICATION_SUBRIP
                "vtt" -> MimeTypes.TEXT_VTT
                else -> MimeTypes.TEXT_SSA
            }
            // « film.fr.srt » → langue « fr ».
            val lang = f.nameWithoutExtension.removePrefix(base).trim('.', '_', '-', ' ').takeIf { it.length in 2..3 }
            MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(f))
                .setMimeType(mime)
                .setLanguage(lang)
                .setLabel(f.name)
                .setSelectionFlags(if (i == 0) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
