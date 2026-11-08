package com.scalari.fichiers.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scalari.fichiers.FileManagerApp
import com.scalari.fichiers.data.Category
import com.scalari.fichiers.data.FileItem
import com.scalari.fichiers.data.MediaQueries
import com.scalari.fichiers.data.OperationManager
import com.scalari.fichiers.data.StorageRoot
import com.scalari.fichiers.data.StorageRoots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val HOME_CATEGORIES = listOf(
    Category.IMAGES, Category.VIDEOS, Category.AUDIO, Category.DOCUMENTS,
    Category.ARCHIVES, Category.APKS, Category.LARGE, Category.RECENT,
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val _roots = MutableStateFlow<List<StorageRoot>>(emptyList())
    val roots: StateFlow<List<StorageRoot>> = _roots.asStateFlow()

    private val _recent = MutableStateFlow<List<FileItem>>(emptyList())
    val recent: StateFlow<List<FileItem>> = _recent.asStateFlow()

    private val _stats = MutableStateFlow<Map<Category, MediaQueries.Stat>>(emptyMap())
    val stats: StateFlow<Map<Category, MediaQueries.Stat>> = _stats.asStateFlow()

    init {
        refresh(withStats = true)
        viewModelScope.launch { OperationManager.changes.collect { refresh(withStats = true) } }
    }

    /** [withStats] : recalcule aussi les compteurs des catégories (requêtes MediaStore plus coûteuses). */
    fun refresh(withStats: Boolean = false) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            _roots.value = withContext(Dispatchers.IO) { StorageRoots.list(ctx) }
            val showHidden = FileManagerApp.instance.settingsState.value.showHidden
            _recent.value = MediaQueries.query(ctx, Category.RECENT, showHidden, limit = 30)
            if (withStats || _stats.value.isEmpty()) {
                val stats = HashMap<Category, MediaQueries.Stat>()
                for (c in HOME_CATEGORIES) stats[c] = MediaQueries.stat(ctx, c, showHidden)
                _stats.value = stats
            }
        }
    }
}
