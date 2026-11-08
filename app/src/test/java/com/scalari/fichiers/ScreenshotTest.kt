@file:OptIn(ExperimentalMaterial3Api::class)

package com.scalari.fichiers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import com.scalari.fichiers.data.AppSettings
import com.scalari.fichiers.data.Category
import com.scalari.fichiers.data.Clip
import com.scalari.fichiers.data.FileItem
import com.scalari.fichiers.data.FileTypes
import com.scalari.fichiers.data.MediaQueries
import com.scalari.fichiers.data.StorageRoot
import com.scalari.fichiers.data.ThemeMode
import com.scalari.fichiers.ui.AppBottomBar
import com.scalari.fichiers.ui.analyzer.AnalyzerContent
import com.scalari.fichiers.ui.analyzer.BootAppsContent
import com.scalari.fichiers.ui.analyzer.UsageSlice
import com.scalari.fichiers.ui.player.AudioPlayerContent
import com.scalari.fichiers.ui.player.FitMode
import com.scalari.fichiers.ui.player.VideoControls
import androidx.compose.foundation.background
import com.scalari.fichiers.ui.player.MiniPlayer
import com.scalari.fichiers.ui.player.PlayerUiState
import com.scalari.fichiers.ui.Tab
import com.scalari.fichiers.ui.browser.Breadcrumbs
import com.scalari.fichiers.ui.browser.PasteBar
import com.scalari.fichiers.ui.browser.SectionHeader
import com.scalari.fichiers.ui.browser.SelectionToolbar
import com.scalari.fichiers.ui.browser.SelectionTopBar
import com.scalari.fichiers.ui.components.FileRow
import com.scalari.fichiers.ui.home.HomeActions
import com.scalari.fichiers.ui.home.HomeContent
import com.scalari.fichiers.ui.home.defaultShortcuts
import com.scalari.fichiers.ui.settings.SettingsScreen
import com.scalari.fichiers.ui.theme.FichiersTheme
import com.scalari.fichiers.ui.vault.PinPad
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Rendu des écrans principaux avec des données fictives.
 * Lancer : ./gradlew recordRoborazziDebug → images dans app/screenshots/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xxhdpi")
class ScreenshotTest {

    private val base = File("/storage/emulated/0")
    private val now = System.currentTimeMillis()

    private fun item(name: String, dir: Boolean = false, size: Long = 0, children: Int = -1, ago: Long = 0) = FileItem(
        file = File(base, "Documents/Travail/$name"),
        name = name,
        isDirectory = dir,
        size = size,
        lastModified = now - ago * 3_600_000L,
        isHidden = false,
        childCount = if (dir) children else -1,
        type = FileTypes.of(name, dir),
    )

    private val folderItems = listOf(
        item("Factures", dir = true, children = 24, ago = 2),
        item("Photos vacances", dir = true, children = 312, ago = 30),
        item("Projets", dir = true, children = 7, ago = 70),
        item("Contrat de location.pdf", size = 2_400_000, ago = 5),
        item("Budget 2026.xlsx", size = 84_000, ago = 26),
        item("Présentation client.pptx", size = 12_800_000, ago = 50),
        item("Notes réunion.md", size = 3_200, ago = 80),
        item("Archive photos.zip", size = 1_250_000_000, ago = 200),
        item("Podcast épisode 12.mp3", size = 48_000_000, ago = 300),
        item("Rapport annuel.docx", size = 640_000, ago = 400),
    )

    private fun capture(
        name: String,
        dark: Boolean = false,
        mode: ThemeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT,
        content: @Composable () -> Unit,
    ) {
        captureRoboImage("screenshots/$name.png") {
            FichiersTheme(AppSettings(themeMode = mode, dynamicColor = false)) {
                Surface(Modifier.fillMaxSize()) { content() }
            }
        }
    }

    @Composable
    private fun Home() {
        HomeContent(
            roots = listOf(
                StorageRoot(base, "Stockage interne", true, false, 128_000_000_000, 46_300_000_000),
                StorageRoot(File("/storage/1A2B-3C4D"), "Carte SD SanDisk", false, true, 64_000_000_000, 51_000_000_000),
            ),
            stats = mapOf(
                Category.IMAGES to MediaQueries.Stat(4_812, 9_800_000_000),
                Category.VIDEOS to MediaQueries.Stat(236, 31_000_000_000),
                Category.AUDIO to MediaQueries.Stat(1_204, 6_100_000_000),
                Category.DOCUMENTS to MediaQueries.Stat(389, 1_200_000_000),
                Category.ARCHIVES to MediaQueries.Stat(41, 3_900_000_000),
                Category.APKS to MediaQueries.Stat(12, 640_000_000),
                Category.LARGE to MediaQueries.Stat(58, 38_400_000_000),
                Category.RECENT to MediaQueries.Stat(143, 2_100_000_000),
            ),
            shortcuts = defaultShortcuts(),
            favorites = listOf("/storage/emulated/0/Documents/Travail"),
            counts = (listOf("/storage/emulated/0/Documents/Travail") + defaultShortcuts().map { it.path })
                .zip(listOf(10, 37, 2, 14, 6, 128, 0)).toMap(),
            actions = HomeActions(),
        )
    }

    @Composable
    private fun Browser(selected: Set<String> = emptySet(), clip: Clip? = null) {
        val dirs = folderItems.filter { it.isDirectory }
        val files = folderItems.filter { !it.isDirectory }
        Scaffold(
            topBar = {
                if (selected.isNotEmpty()) {
                    SelectionTopBar(count = selected.size, onClose = {}, onSelectAll = {})
                } else {
                    TopAppBar(
                        title = {
                            Column {
                                Text("Travail")
                                Text("10 éléments", style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        navigationIcon = { IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                        actions = {
                            IconButton(onClick = {}) { Icon(Icons.Filled.Search, null) }
                            IconButton(onClick = {}) { Icon(Icons.Filled.GridView, null) }
                            IconButton(onClick = {}) { Icon(Icons.Filled.MoreVert, null) }
                        },
                    )
                }
            },
            bottomBar = {
                if (selected.isNotEmpty()) {
                    SelectionToolbar(
                        single = null, isFavorite = { false }, onCopy = {}, onCut = {}, onDelete = {}, onShare = {},
                        onRename = {}, onCompress = {}, onProperties = {}, onOpenWith = {}, onToggleFavorite = {}, onVault = {},
                    )
                } else if (clip != null) {
                    PasteBar(clip, onPaste = {}, onCancel = {})
                }
            },
            floatingActionButton = {
                if (selected.isEmpty() && clip == null) {
                    ExtendedFloatingActionButton(
                        onClick = {},
                        icon = { Icon(Icons.Filled.CreateNewFolder, null) },
                        text = { Text("Nouveau dossier") },
                    )
                }
            },
        ) { padding ->
            Column(Modifier.padding(padding)) {
                Breadcrumbs(base, "Stockage interne", File(base, "Documents/Travail"), onClick = {})
                LazyColumn {
                    item { SectionHeader("Dossiers", dirs.size) }
                    items(dirs) { FileRow(it, it.path in selected, onClick = {}, onLongClick = {}) }
                    item { SectionHeader("Fichiers", files.size) }
                    items(files) { FileRow(it, it.path in selected, onClick = {}, onLongClick = {}) }
                }
            }
        }
    }

    @Test fun home_light() = capture("01_accueil_clair") { Home() }
    @Test fun home_dark() = capture("02_accueil_sombre", dark = true) { Home() }
    @Test fun browser_light() = capture("03_explorateur_clair") { Browser() }
    @Test fun browser_dark() = capture("04_explorateur_sombre", dark = true) { Browser() }

    @Test fun browser_selection() = capture("05_selection") {
        Browser(selected = setOf(folderItems[3].path, folderItems[5].path, folderItems[7].path))
    }

    @Test fun browser_paste() = capture("06_coller") {
        Browser(clip = Clip(listOf(File("/x/Contrat.pdf"), File("/x/Budget.xlsx")), cut = true))
    }

    @Test fun vault_pin() = capture("07_coffre_code", dark = true) {
        Column { PinPad("Coffre verrouillé", "Saisissez votre code", null, false, 0, onBiometric = {}) {} }
    }

    @Test fun home_amoled_tabs() = capture("09_accueil_amoled_onglets", mode = ThemeMode.BLACK) {
        Scaffold(bottomBar = { AppBottomBar(Tab.HOME) {} }) { p -> Column(Modifier.padding(p)) { Home() } }
    }

    @Test fun browser_amoled_tabs() = capture("10_explorateur_amoled_onglets", mode = ThemeMode.BLACK) {
        Scaffold(bottomBar = { AppBottomBar(Tab.FILES) {} }) { p -> Column(Modifier.padding(p)) { Browser() } }
    }

    @Test fun light_tabs() = capture("11_accueil_clair_onglets") {
        Scaffold(bottomBar = { AppBottomBar(Tab.HOME) {} }) { p -> Column(Modifier.padding(p)) { Home() } }
    }

    @Test fun audio_player() = capture("12_lecteur_audio", mode = ThemeMode.BLACK) {
        AudioPlayerContent(
            state = PlayerUiState(
                hasMedia = true, isPlaying = true, title = "Podcast épisode 12", subtitle = "Les Ondes · Saison 3",
                position = 754_000, duration = 2_712_000, index = 11, count = 24, shuffle = true,
            ),
            onBack = {}, onPlayPause = {}, onPrevious = {}, onNext = {}, onSeek = {}, onShuffle = {}, onRepeat = {},
        )
    }

    @Test fun mini_player() = capture("13_mini_lecteur") {
        Scaffold(bottomBar = {
            Column {
                MiniPlayer(
                    PlayerUiState(hasMedia = true, isPlaying = true, title = "Podcast épisode 12", subtitle = "Les Ondes",
                        position = 754_000, duration = 2_712_000),
                    onOpen = {}, onPlayPause = {}, onNext = {}, onClose = {},
                )
                AppBottomBar(Tab.HOME) {}
            }
        }) { p -> Column(Modifier.padding(p)) { Home() } }
    }

    @Test fun analyzer() = capture("14_analyse") {
        AnalyzerContent(
            root = StorageRoot(base, "Stockage interne", true, false, 128_000_000_000, 46_300_000_000),
            slices = listOf(
                UsageSlice("Images", 9_800_000_000, androidx.compose.ui.graphics.Color(0xFF2E9E5B)),
                UsageSlice("Vidéos", 31_000_000_000, androidx.compose.ui.graphics.Color(0xFFE5484D)),
                UsageSlice("Audio", 6_100_000_000, androidx.compose.ui.graphics.Color(0xFF8E4EC6)),
                UsageSlice("Documents", 1_200_000_000, androidx.compose.ui.graphics.Color(0xFF3E7BFA)),
                UsageSlice("APK", 640_000_000, androidx.compose.ui.graphics.Color(0xFF5BA33B)),
                UsageSlice("Archives", 3_900_000_000, androidx.compose.ui.graphics.Color(0xFFB0793F)),
                UsageSlice("Système et autres", 29_060_000_000, androidx.compose.ui.graphics.Color(0xFF9AA0AE)),
            ),
        )
    }

    @Test fun boot_apps() = capture("15_demarrage") {
        BootAppsContent(
            apps = listOf(
                com.scalari.fichiers.data.BootApp("com.ex.meteo", "Météo Plus", null, false, false, false, 1),
                com.scalari.fichiers.data.BootApp("com.ex.vpn", "Mon VPN", null, false, true, false, 2),
                com.scalari.fichiers.data.BootApp("com.ex.nettoyeur", "Super Nettoyeur", null, false, false, false, 1),
                com.scalari.fichiers.data.BootApp("com.ex.sync", "Synchro Photos", null, false, false, true, 1),
            ),
            showSystem = false, onToggleSystem = {}, onSelect = {},
        )
    }

    @Test fun video_controls() = capture("16_lecteur_video", mode = ThemeMode.DARK) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(
                    androidx.compose.ui.graphics.Color(0xFF203A43), androidx.compose.ui.graphics.Color(0xFF2C5364),
                )))
        ) {
            VideoControls(
                visible = true, title = "Vacances en Bretagne 2026.mp4", isPlaying = true,
                position = 754_000, duration = 2_712_000, speed = 1.25f, fit = FitMode.FIT,
                hasPrev = true, hasNext = true, hasSubtitles = true, hasAudioChoice = true,
            )
        }
    }

    @Test fun settings() = capture("08_parametres") { SettingsScreen(onBack = {}) }
}
