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

/** Délai avant le verrouillage du coffre quand l'appli passe en arrière-plan. */
enum class VaultLockDelay(val seconds: Int, val label: String) {
    IMMEDIATE(0, "Immédiatement"),
    S30(30, "Après 30 secondes"),
    M1(60, "Après 1 minute"),
    M5(300, "Après 5 minutes"),
    M15(900, "Après 15 minutes"),
}

/** Durée de conservation des éléments dans la corbeille. */
enum class TrashRetention(val days: Int, val label: String) {
    D7(7, "7 jours"),
    D30(30, "30 jours"),
    D90(90, "90 jours"),
    NEVER(0, "Jamais (vidage manuel)"),
}

/** Tri et affichage propres à un dossier (remplacent les réglages généraux dans ce dossier). */
data class FolderView(val sortBy: SortBy, val ascending: Boolean, val grid: Boolean)

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
    val vaultLockDelay: VaultLockDelay = VaultLockDelay.IMMEDIATE,
    val trashRetention: TrashRetention = TrashRetention.D30,
    /** Chemin absolu du dossier → tri et affichage mémorisés. */
    val folderViews: Map<String, FolderView> = emptyMap(),
) {
    /** Réglages effectifs dans [path] : ceux du dossier s'il en a, sinon les généraux. */
    fun forFolder(path: String): AppSettings =
        folderViews[path]?.let { copy(sortBy = it.sortBy, ascending = it.ascending, gridView = it.grid) } ?: this
}

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
        val VAULT_LOCK_DELAY = stringPreferencesKey("vault_lock_delay")
        val TRASH_RETENTION = stringPreferencesKey("trash_retention")
        val FOLDER_VIEWS = stringSetPreferencesKey("folder_views")
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
                vaultLockDelay = p[Keys.VAULT_LOCK_DELAY]?.let { runCatching { VaultLockDelay.valueOf(it) }.getOrNull() } ?: VaultLockDelay.IMMEDIATE,
                trashRetention = p[Keys.TRASH_RETENTION]?.let { runCatching { TrashRetention.valueOf(it) }.getOrNull() } ?: TrashRetention.D30,
                folderViews = p[Keys.FOLDER_VIEWS].orEmpty().mapNotNull(::decodeFolderView).toMap(),
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
    suspend fun setVaultLockDelay(value: VaultLockDelay) { context.dataStore.edit { it[Keys.VAULT_LOCK_DELAY] = value.name } }
    suspend fun setTrashRetention(value: TrashRetention) { context.dataStore.edit { it[Keys.TRASH_RETENTION] = value.name } }

    /** Mémorise (ou oublie si [view] est null) le tri et l'affichage propres à [path]. */
    suspend fun setFolderView(path: String, view: FolderView?) {
        context.dataStore.edit { prefs ->
            val others = prefs[Keys.FOLDER_VIEWS].orEmpty().filterTo(HashSet()) { decodeFolderView(it)?.first != path }
            if (view != null) others += encodeFolderView(path, view)
            prefs[Keys.FOLDER_VIEWS] = others
        }
    }

    suspend fun clearFolderViews() { context.dataStore.edit { it.remove(Keys.FOLDER_VIEWS) } }

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

private const val SEP = '\u0000'

internal fun encodeFolderView(path: String, v: FolderView) = listOf(path, v.sortBy.name, v.ascending, v.grid).joinToString(SEP.toString())

internal fun decodeFolderView(raw: String): Pair<String, FolderView>? {
    val parts = raw.split(SEP)
    if (parts.size != 4) return null
    val sort = runCatching { SortBy.valueOf(parts[1]) }.getOrNull() ?: return null
    return parts[0] to FolderView(sort, parts[2] == "true", parts[3] == "true")
}
