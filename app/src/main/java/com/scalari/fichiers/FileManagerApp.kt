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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.scalari.fichiers.data.OperationManager
import com.scalari.fichiers.data.TrashManager
import com.scalari.fichiers.widget.StorageWidget

@OptIn(kotlinx.coroutines.FlowPreview::class)
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
        runCatching { com.scalari.fichiers.update.UpdateManager.schedule(this) }
        appScope.launch {
            // Corbeille : suppression automatique des éléments expirés.
            val days = settings.settings.first().trashRetention.days
            withContext(Dispatchers.IO) { runCatching { TrashManager.purgeExpired(this@FileManagerApp, days) } }
        }
        appScope.launch {
            // Widget : suit les favoris et les changements de fichiers (espace libre).
            merge(
                settingsState.map { it.favorites }.distinctUntilChanged().drop(1).map { },
                OperationManager.changes,
            ).debounce(1_000).collect { StorageWidget.refresh(this@FileManagerApp) }
        }
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
