package com.scalari.fichiers.ui

import android.net.Uri
import androidx.compose.material3.SnackbarHostState
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
import com.scalari.fichiers.ui.viewer.ImageViewerScreen
import com.scalari.fichiers.ui.viewer.TextEditorScreen

val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> { error("SnackbarHostState absent") }

object Routes {
    const val HOME = "home"
    const val BROWSER = "browser?path={path}&search={search}"
    const val CATEGORY = "category/{name}"
    const val TEXT = "text?path={path}"
    const val IMAGE = "image?path={path}"
    const val TRASH = "trash"
    const val SETTINGS = "settings"

    fun browser(path: String?, search: Boolean = false) =
        "browser?path=${Uri.encode(path ?: "")}&search=$search"

    fun category(c: Category) = "category/${c.name}"
    fun text(path: String) = "text?path=${Uri.encode(path)}"
    fun image(path: String) = "image?path=${Uri.encode(path)}"
}

/** Actions de navigation partagées par les écrans. */
class AppNavigator(private val nav: NavHostController) {
    fun back() { nav.popBackStack() }
    fun browser(path: String?, search: Boolean = false) = nav.navigate(Routes.browser(path, search))
    fun category(c: Category) = nav.navigate(Routes.category(c))
    fun text(path: String) = nav.navigate(Routes.text(path))
    fun image(path: String) = nav.navigate(Routes.image(path))
    fun trash() = nav.navigate(Routes.TRASH)
    fun settings() = nav.navigate(Routes.SETTINGS)
}

@Composable
fun FichiersApp() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(StoragePermission.isGranted(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = StoragePermission.isGranted(context) }

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

@Composable
private fun AppNavHost() {
    val nav = rememberNavController()
    val navigator = remember(nav) { AppNavigator(nav) }
    val pathArg = navArgument("path") {
        type = NavType.StringType
        nullable = true
        defaultValue = null
    }

    NavHost(navController = nav, startDestination = Routes.HOME) {
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
        composable(Routes.IMAGE, arguments = listOf(pathArg)) { entry ->
            ImageViewerScreen(entry.arguments?.getString("path") ?: "", onBack = navigator::back)
        }
        composable(Routes.TRASH) {
            TrashScreen(onBack = navigator::back)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = navigator::back)
        }
    }
}
