package com.scalari.fichiers.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode(val label: String) { SYSTEM("Système"), LIGHT("Clair"), DARK("Sombre"), BLACK("Noir pur (AMOLED)") }

enum class SortBy(val label: String) { NAME("Nom"), SIZE("Taille"), DATE("Date de modification"), TYPE("Type") }

data class AppSettings(
    val showHidden: Boolean = false,
    val sortBy: SortBy = SortBy.NAME,
    val ascending: Boolean = true,
    val foldersFirst: Boolean = true,
    val gridView: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val confirmDelete: Boolean = true,
    val useTrash: Boolean = true,
    val favorites: List<String> = emptyList(),
    /** Autorise captures et enregistrement d'écran dans le coffre (bloqués par défaut). */
    val vaultScreenshots: Boolean = false,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val SHOW_HIDDEN = booleanPreferencesKey("show_hidden")
        val SORT_BY = stringPreferencesKey("sort_by")
        val ASCENDING = booleanPreferencesKey("ascending")
        val FOLDERS_FIRST = booleanPreferencesKey("folders_first")
        val GRID_VIEW = booleanPreferencesKey("grid_view")
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val CONFIRM_DELETE = booleanPreferencesKey("confirm_delete")
        val USE_TRASH = booleanPreferencesKey("use_trash")
        val FAVORITES = stringSetPreferencesKey("favorites")
        val VAULT_SCREENSHOTS = booleanPreferencesKey("vault_screenshots")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            AppSettings(
                showHidden = p[Keys.SHOW_HIDDEN] ?: false,
                sortBy = p[Keys.SORT_BY]?.let { runCatching { SortBy.valueOf(it) }.getOrNull() } ?: SortBy.NAME,
                ascending = p[Keys.ASCENDING] ?: true,
                foldersFirst = p[Keys.FOLDERS_FIRST] ?: true,
                gridView = p[Keys.GRID_VIEW] ?: false,
                themeMode = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
                dynamicColor = p[Keys.DYNAMIC_COLOR] ?: false,
                confirmDelete = p[Keys.CONFIRM_DELETE] ?: true,
                useTrash = p[Keys.USE_TRASH] ?: true,
                favorites = p[Keys.FAVORITES]?.sortedBy { it.lowercase() } ?: emptyList(),
                vaultScreenshots = p[Keys.VAULT_SCREENSHOTS] ?: false,
            )
        }

    suspend fun setShowHidden(value: Boolean) { context.dataStore.edit { it[Keys.SHOW_HIDDEN] = value } }
    suspend fun setSort(sortBy: SortBy, ascending: Boolean) {
        context.dataStore.edit {
            it[Keys.SORT_BY] = sortBy.name
            it[Keys.ASCENDING] = ascending
        }
    }
    suspend fun setFoldersFirst(value: Boolean) { context.dataStore.edit { it[Keys.FOLDERS_FIRST] = value } }
    suspend fun setGridView(value: Boolean) { context.dataStore.edit { it[Keys.GRID_VIEW] = value } }
    suspend fun setThemeMode(value: ThemeMode) { context.dataStore.edit { it[Keys.THEME] = value.name } }
    suspend fun setDynamicColor(value: Boolean) { context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = value } }
    suspend fun setConfirmDelete(value: Boolean) { context.dataStore.edit { it[Keys.CONFIRM_DELETE] = value } }
    suspend fun setUseTrash(value: Boolean) { context.dataStore.edit { it[Keys.USE_TRASH] = value } }
    suspend fun setVaultScreenshots(value: Boolean) { context.dataStore.edit { it[Keys.VAULT_SCREENSHOTS] = value } }

    suspend fun toggleFavorite(path: String) {
        context.dataStore.edit {
            val current = it[Keys.FAVORITES] ?: emptySet()
            it[Keys.FAVORITES] = if (path in current) current - path else current + path
        }
    }

    suspend fun removeFavorite(path: String) {
        context.dataStore.edit {
            it[Keys.FAVORITES] = (it[Keys.FAVORITES] ?: emptySet()) - path
        }
    }
}
