package com.scalari.fichiers

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.scalari.fichiers.data.AppSettings
import com.scalari.fichiers.data.SettingsRepository
import com.scalari.fichiers.data.vault.VaultManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class FileManagerApp : Application(), ImageLoaderFactory {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var settings: SettingsRepository
        private set
    lateinit var settingsState: StateFlow<AppSettings>
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsRepository(this)
        settingsState = settings.settings.stateIn(appScope, SharingStarted.Eagerly, AppSettings())
        VaultManager.clearTemp(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(120)
            .build()

    companion object {
        lateinit var instance: FileManagerApp
            private set
    }
}
