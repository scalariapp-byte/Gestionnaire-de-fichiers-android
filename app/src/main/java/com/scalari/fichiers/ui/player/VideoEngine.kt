package com.scalari.fichiers.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Piste audio ou de sous-titres proposée à l'utilisateur. */
data class TrackOption(val id: String, val label: String, val selected: Boolean)

/**
 * Moteur de lecture vidéo : celui d'Android (Media3/ExoPlayer) ou VLC.
 * L'interface du lecteur (gestes, commandes, reprise…) est la même pour les deux.
 * Les propriétés sont lues régulièrement par l'interface (pas d'abonnement nécessaire).
 */
interface VideoEngine {
    /** Nom affiché (« Android » ou « VLC »). */
    val engineName: String
    val mediaId: String
    val title: String
    val isPlaying: Boolean
    val position: Long
    val duration: Long
    val speed: Float
    val videoWidth: Int
    val videoHeight: Int
    val hasNext: Boolean
    val hasPrev: Boolean

    fun play()
    fun pause()
    fun seekTo(ms: Long)
    fun setSpeed(speed: Float)
    fun next()
    fun prev()
    fun audioTracks(): List<TrackOption>
    fun textTracks(): List<TrackOption>
    fun selectAudio(track: TrackOption)
    /** null = sous-titres désactivés. */
    fun selectText(track: TrackOption?)

    /** Surface d'affichage de la vidéo. */
    @Composable
    fun VideoSurface(fit: FitMode, modifier: Modifier)
}
