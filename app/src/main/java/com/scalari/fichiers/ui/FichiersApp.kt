package com.scalari.fichiers.ui

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import com.scalari.fichiers.ui.player.MiniPlayer
import com.scalari.fichiers.ui.player.PlayerConnection
import com.scalari.fichiers.ui.player.PlayerScreen
import com.scalari.fichiers.ui.player.rememberPlayerState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.scalari.fichiers.data.Category
import com.scalari.fichiers.data.OperationManager
import com.scalari.fichiers.data.StoragePermission
import com.scalari.fichiers.ui.browser.BrowserScreen
import com.scalari.fichiers.ui.category.CategoryScreen
import com.scalari.fichiers.ui.components.OperationProgressDialog
import com.scalari.fichiers.ui.home.HomeScreen
import com.scalari.fichiers.ui.permission.PermissionScreen
import com.scalari.fichiers.ui.settings.SettingsScreen
import com.scalari.fichiers.ui.trash.TrashScreen
import com.scalari.fichiers.ui.analyzer.AnalyzerScreen
import com.scalari.fichiers.ui.analyzer.DuplicatesScreen
import com.scalari.fichiers.ui.analyzer.EmptyFoldersScreen
import com.scalari.fichiers.ui.analyzer.FolderSizesScreen
import com.scalari.fichiers.ui.analyzer.InstalledApksScreen
import com.scalari.fichiers.ui.vault.VaultScreen
import com.scalari.fichiers.data.vault.VaultManager
import com.scalari.fichiers.ui.viewer.ImageViewerScreen
import com.scalari.fichiers.ui.viewer.TextEditorScreen

val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> { error("SnackbarHostState absent") }

object Routes {
    const val HOME = "home"
    const val BROWSER = "browser?path={path}&search={search}"
    const val CATEGORY = "category/{name}"
    const val TEXT = "text?path={path}"
    const val IMAGE = "image?path={path}&secure={secure}"
    const val VAULT = "vault"
    const val TAB_FILES = "files"
    const val ANALYZER = "analyzer"
    const val ANALYZER_FOLDERS = "analyzer/folders?path={path}"
    const val ANALYZER_DUPLICATES = "analyzer/duplicates"
    const val ANALYZER_APKS = "analyzer/apks"
    const val ANALYZER_EMPTY = "analyzer/empty"
    const val BOOT_APPS = "boot-apps"
    const val PLAYER = "player?path={path}&secure={secure}"
    fun player(path: String, secure: Boolean = false) = "player?path=${Uri.encode(path)}&secure=$secure"
    const val TAB_RECENT = "recent"
    const val TRASH = "trash"
    const val SETTINGS = "settings"

    fun browser(path: String?, search: Boolean = false) =
        "browser?path=${Uri.encode(path ?: "")}&search=$search"

    fun category(c: Category) = "category/${c.name}"
    fun text(path: String) = "text?path=${Uri.encode(path)}"
    fun image(path: String, secure: Boolean = false) = "image?path=${Uri.encode(path)}&secure=$secure"
}

/** Actions de navigation partagées par les écrans. */
class AppNavigator(private val nav: NavHostController) {
    fun back() { nav.popBackStack() }

    /** Changement d'onglet : une seule copie par onglet, état conservé (défilement, dossier). */
    fun tab(tab: Tab) {
        if (tab == Tab.HOME) {
            // L'accueil vide toujours la pile : on y revient quel que soit l'écran ouvert.
            nav.popBackStack(nav.graph.findStartDestination().id, inclusive = false)
            return
        }
        if (nav.currentDestination?.route == tab.route) return
        nav.navigate(tab.route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    fun browser(path: String?, search: Boolean = false) = nav.navigate(Routes.browser(path, search))
    fun category(c: Category) = if (c == Category.RECENT) tab(Tab.RECENT) else nav.navigate(Routes.category(c))
    fun text(path: String) = nav.navigate(Routes.text(path))
    fun image(path: String, secure: Boolean = false) = nav.navigate(Routes.image(path, secure))
    fun vault() = tab(Tab.VAULT)
    fun analyzer() = nav.navigate(Routes.ANALYZER)
    fun analyzerFolders(path: String?) = nav.navigate("analyzer/folders?path=${Uri.encode(path ?: "")}")
    fun analyzerDuplicates() = nav.navigate(Routes.ANALYZER_DUPLICATES)
    fun analyzerApks() = nav.navigate(Routes.ANALYZER_APKS)
    fun analyzerEmpty() = nav.navigate(Routes.ANALYZER_EMPTY)
    fun bootApps() = nav.navigate(Routes.BOOT_APPS)
    fun player(path: String, secure: Boolean = false) = nav.navigate(Routes.player(path, secure)) { launchSingleTop = true }

    /** Ouvre un fichier avec la visionneuse intégrée adaptée ; false s'il faut une appli externe. */
    fun openInternal(item: com.scalari.fichiers.data.FileItem): Boolean = when {
        item.type == com.scalari.fichiers.data.FileType.IMAGE -> { image(item.path); true }
        item.type == com.scalari.fichiers.data.FileType.VIDEO || item.type == com.scalari.fichiers.data.FileType.AUDIO -> { player(item.path); true }
        com.scalari.fichiers.data.FileTypes.isTextEditable(item) -> { text(item.path); true }
        else -> false
    }
    fun trash() = nav.navigate(Routes.TRASH)
    fun settings() = nav.navigate(Routes.SETTINGS)
}

@Composable
fun FichiersApp() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(StoragePermission.isGranted(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = StoragePermission.isGranted(context) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (VaultManager.externalOpen) VaultManager.externalOpen = false else VaultManager.lock(context)
    }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        OperationManager.messages.collect { snackbar.showSnackbar(it) }
    }
    val progress by OperationManager.progress.collectAsStateWithLifecycle()

    CompositionLocalProvider(LocalSnackbarHostState provides snackbar) {
        Surface {
            if (!granted) {
                PermissionScreen(onGranted = { granted = StoragePermission.isGranted(context) })
            } else {
                AppNavHost()
            }
            progress?.let { OperationProgressDialog(it, onCancel = OperationManager::cancel) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppNavHost() {
    val nav = rememberNavController()
    val navigator = remember(nav) { AppNavigator(nav) }
    val pathArg = navArgument("path") {
        type = NavType.StringType
        nullable = true
        defaultValue = null
    }

    val entry by nav.currentBackStackEntryAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) { PlayerConnection.connect(context) }
    val controller by PlayerConnection.controller.collectAsStateWithLifecycle()
    val playerState = rememberPlayerState(controller)
    val currentTab = tabForRoute(entry?.destination?.route)
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            Column {
                val showMini = playerState.hasMedia && !playerState.isVideo &&
                    entry?.destination?.route != Routes.PLAYER && entry?.destination?.route != Routes.IMAGE
                AnimatedVisibility(visible = showMini, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                    Box(if (currentTab == null) Modifier.navigationBarsPadding() else Modifier) {
                        MiniPlayer(
                            state = playerState,
                            onOpen = { navigator.player("") },
                            onPlayPause = { controller?.let { if (it.isPlaying) it.pause() else it.play() } },
                            onNext = { controller?.seekToNext() },
                            onClose = { controller?.run { stop(); clearMediaItems() } },
                        )
                    }
                }
                AnimatedVisibility(
                    visible = currentTab != null,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it },
                ) { AppBottomBar(currentTab, onSelect = navigator::tab) }
            }
        },
    ) { barPadding ->
    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        modifier = Modifier
            .padding(barPadding)
            .consumeWindowInsets(barPadding),
        enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 12 } },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(240)) { it / 12 } },
    ) {
        composable(Routes.HOME) {
            HomeScreen(navigator)
        }
        composable(
            Routes.BROWSER,
            arguments = listOf(
                pathArg,
                navArgument("search") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { entry ->
            BrowserScreen(
                startPath = entry.arguments?.getString("path")?.takeIf { it.isNotEmpty() },
                openSearch = entry.arguments?.getBoolean("search") ?: false,
                navigator = navigator,
            )
        }
        composable(Routes.CATEGORY, arguments = listOf(navArgument("name") { type = NavType.StringType })) { entry ->
            val category = runCatching { Category.valueOf(entry.arguments?.getString("name") ?: "") }
                .getOrDefault(Category.IMAGES)
            CategoryScreen(category, navigator)
        }
        composable(Routes.TEXT, arguments = listOf(pathArg)) { entry ->
            TextEditorScreen(entry.arguments?.getString("path") ?: "", onBack = navigator::back)
        }
        composable(
            Routes.IMAGE,
            arguments = listOf(pathArg, navArgument("secure") { type = NavType.BoolType; defaultValue = false }),
        ) { entry ->
            ImageViewerScreen(
                path = entry.arguments?.getString("path") ?: "",
                secure = entry.arguments?.getBoolean("secure") ?: false,
                onBack = navigator::back,
            )
        }
        composable(Routes.VAULT) {
            VaultScreen(navigator, isTab = true)
        }
        composable(Routes.TAB_FILES) {
            BrowserScreen(startPath = null, openSearch = false, navigator = navigator, isTab = true)
        }
        composable(Routes.TAB_RECENT) {
            CategoryScreen(Category.RECENT, navigator, isTab = true)
        }
        composable(
            Routes.PLAYER,
            arguments = listOf(pathArg, navArgument("secure") { type = NavType.BoolType; defaultValue = false }),
        ) { e ->
            PlayerScreen(
                e.arguments?.getString("path") ?: "",
                onBack = navigator::back,
                secure = e.arguments?.getBoolean("secure") ?: false,
            )
        }
        composable(Routes.ANALYZER) { AnalyzerScreen(navigator) }
        composable(Routes.ANALYZER_FOLDERS, arguments = listOf(pathArg)) { e ->
            FolderSizesScreen(e.arguments?.getString("path")?.takeIf { it.isNotEmpty() }, navigator)
        }
        composable(Routes.ANALYZER_DUPLICATES) { DuplicatesScreen(navigator) }
        composable(Routes.ANALYZER_APKS) { InstalledApksScreen(navigator) }
        composable(Routes.ANALYZER_EMPTY) { EmptyFoldersScreen(navigator) }
        composable(Routes.BOOT_APPS) { com.scalari.fichiers.ui.analyzer.BootAppsScreen(onBack = navigator::back) }
        composable(Routes.TRASH) {
            TrashScreen(onBack = navigator::back)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = navigator::back)
        }
    }
    }
}
