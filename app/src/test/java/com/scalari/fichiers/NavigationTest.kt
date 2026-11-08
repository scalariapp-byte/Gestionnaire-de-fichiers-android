package com.scalari.fichiers

import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.scalari.fichiers.data.Category
import com.scalari.fichiers.ui.AppNavigator
import com.scalari.fichiers.ui.Routes
import com.scalari.fichiers.ui.Tab
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Parcours de navigation entre onglets et écrans, sur le même graphe que l'application. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavigationTest {

    @get:Rule
    val rule = createComposeRule()

    private lateinit var nav: NavHostController
    private lateinit var navigator: AppNavigator

    @Before
    fun setUp() {
        rule.setContent {
            val n = rememberNavController()
            nav = n
            navigator = remember(n) { AppNavigator(n) }
            NavHost(n, startDestination = Routes.HOME) {
                composable(Routes.HOME) {}
                composable(
                    Routes.BROWSER,
                    arguments = listOf(
                        navArgument("path") { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("search") { type = NavType.BoolType; defaultValue = false },
                    ),
                ) {}
                composable(Routes.CATEGORY, arguments = listOf(navArgument("name") { type = NavType.StringType })) {}
                composable(Routes.TAB_FILES) {}
                composable(Routes.TAB_RECENT) {}
                composable(Routes.VAULT) {}
                composable(Routes.SETTINGS) {}
            }
        }
        rule.waitForIdle()
    }

    private fun act(block: AppNavigator.() -> Unit) {
        rule.runOnIdle { navigator.block() }
        rule.waitForIdle()
    }

    private fun route() = nav.currentDestination?.route

    @Test
    fun accueil_depuis_dossier_ouvert_sur_l_accueil() {
        act { browser("/storage/emulated/0/Download") }
        assertEquals(Routes.BROWSER, route())
        act { tab(Tab.HOME) }
        assertEquals(Routes.HOME, route())
    }

    @Test
    fun accueil_depuis_categorie() {
        act { category(Category.IMAGES) }
        assertEquals(Routes.CATEGORY, route())
        act { tab(Tab.HOME) }
        assertEquals(Routes.HOME, route())
    }

    @Test
    fun accueil_depuis_onglet_fichiers_puis_dossier() {
        act { tab(Tab.FILES) }
        act { browser("/storage/emulated/0/DCIM") }
        act { tab(Tab.HOME) }
        assertEquals(Routes.HOME, route())
        assertEquals("la pile doit être vide sous l'accueil", null, nav.previousBackStackEntry)
    }

    @Test
    fun changements_d_onglets_successifs() {
        act { tab(Tab.FILES) }
        assertEquals(Routes.TAB_FILES, route())
        act { tab(Tab.RECENT) }
        assertEquals(Routes.TAB_RECENT, route())
        act { tab(Tab.VAULT) }
        assertEquals(Routes.VAULT, route())
        act { tab(Tab.HOME) }
        assertEquals(Routes.HOME, route())
        act { tab(Tab.FILES) }
        assertEquals(Routes.TAB_FILES, route())
        act { tab(Tab.HOME) }
        assertEquals(Routes.HOME, route())
    }

    @Test
    fun dossier_puis_onglet_fichiers_puis_accueil() {
        act { browser("/storage/emulated/0/Music") }
        act { tab(Tab.FILES) }
        assertEquals(Routes.TAB_FILES, route())
        act { tab(Tab.HOME) }
        assertEquals(Routes.HOME, route())
    }

    @Test
    fun retour_depuis_onglet_revient_a_l_accueil() {
        act { tab(Tab.RECENT) }
        act { back() }
        assertEquals(Routes.HOME, route())
    }
}
