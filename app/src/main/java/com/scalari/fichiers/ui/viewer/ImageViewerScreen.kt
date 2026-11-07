@file:OptIn(ExperimentalMaterial3Api::class)

package com.scalari.fichiers.ui.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.scalari.fichiers.FileManagerApp
import com.scalari.fichiers.data.FileType
import com.scalari.fichiers.data.FileTypes
import com.scalari.fichiers.data.OperationManager
import com.scalari.fichiers.ui.FileOpener
import com.scalari.fichiers.ui.components.ConfirmDialog
import com.scalari.fichiers.ui.components.PropertiesDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Suppress("DEPRECATION")
@Composable
fun ImageViewerScreen(path: String, onBack: () -> Unit) {
    val settings by FileManagerApp.instance.settingsState.collectAsStateWithLifecycle()
    var images by remember { mutableStateOf<List<File>?>(null) }
    var startIndex by remember { mutableStateOf(0) }

    LaunchedEffect(path) {
        val start = File(path)
        val siblings = withContext(Dispatchers.IO) {
            start.parentFile?.listFiles()
                ?.filter { it.isFile && FileTypes.of(it.name, false) == FileType.IMAGE && (settings.showHidden || !it.name.startsWith(".")) }
                ?.sortedWith(compareBy<File, String>(com.scalari.fichiers.data.NaturalOrder) { it.name })
                ?: emptyList()
        }
        val list = siblings.ifEmpty { listOf(start) }
        startIndex = list.indexOfFirst { it.absolutePath == start.absolutePath }.coerceAtLeast(0)
        images = list
    }

    val list = images
    when {
        list == null -> Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }

        list.isEmpty() -> LaunchedEffect(Unit) { onBack() }

        else -> ImagePager(
            list = list,
            startIndex = startIndex,
            confirmBeforeDelete = settings.confirmDelete,
            useTrash = settings.useTrash,
            onBack = onBack,
            onRemoved = { removed -> images = list - removed },
        )
    }
}

@Suppress("DEPRECATION")
@Composable
private fun ImagePager(
    list: List<File>,
    startIndex: Int,
    confirmBeforeDelete: Boolean,
    useTrash: Boolean,
    onBack: () -> Unit,
    onRemoved: (File) -> Unit,
) {
    val context = LocalContext.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val pager = rememberPagerState(initialPage = startIndex) { list.size }
        var chrome by remember { mutableStateOf(true) }
        var zoomed by remember { mutableStateOf(false) }
        var confirmDelete by remember { mutableStateOf(false) }
        var info by remember { mutableStateOf(false) }
        HorizontalPager(
            state = pager,
            userScrollEnabled = !zoomed,
            beyondViewportPageCount = 1,
            key = { list[it].absolutePath },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            ZoomableImage(
                file = list[page],
                active = page == pager.currentPage,
                onTap = { chrome = !chrome },
                onZoomChanged = { if (page == pager.currentPage) zoomed = it },
            )
        }

        val current = list.getOrNull(pager.currentPage) ?: list.last()
        AnimatedVisibility(visible = chrome, enter = fadeIn(), exit = fadeOut()) {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.55f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                title = {
                    Column {
                        Text(current.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${pager.currentPage + 1} / ${list.size}", style = MaterialTheme.typography.bodySmall)
                    }
                },
                actions = {
                    IconButton(onClick = { FileOpener.share(context, listOf(current)) }) { Icon(Icons.Filled.Share, "Partager") }
                    IconButton(onClick = { FileOpener.openExternal(context, current, forceChooser = true) }) {
                        Icon(Icons.Filled.OpenInNew, "Ouvrir avec")
                    }
                    IconButton(onClick = { info = true }) { Icon(Icons.Filled.Info, "Propriétés") }
                    IconButton(onClick = {
                        if (confirmBeforeDelete) confirmDelete = true
                        else {
                            OperationManager.delete(listOf(current), useTrash)
                            onRemoved(current)
                        }
                    }) { Icon(Icons.Filled.Delete, "Supprimer") }
                },
            )
        }

        if (confirmDelete) {
            ConfirmDialog(
                title = if (useTrash) "Mettre à la corbeille ?" else "Supprimer définitivement ?",
                text = "« ${current.name} »",
                confirmLabel = if (useTrash) "Corbeille" else "Supprimer",
                destructive = !useTrash,
                onDismiss = { confirmDelete = false },
                onConfirm = {
                    confirmDelete = false
                    OperationManager.delete(listOf(current), useTrash)
                    onRemoved(current)
                },
            )
        }
        if (info) PropertiesDialog(listOf(current), onDismiss = { info = false })
    }
}

@Composable
private fun ZoomableImage(file: File, active: Boolean, onTap: () -> Unit, onZoomChanged: (Boolean) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(active) {
        if (!active) {
            scale = 1f
            offset = Offset.Zero
        }
    }

    fun clamp(o: Offset, s: Float, width: Int, height: Int): Offset {
        val maxX = width * (s - 1) / 2f
        val maxY = height * (s - 1) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    AsyncImage(
        model = file,
        contentDescription = file.name,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(file) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val multiTouch = event.changes.count { it.pressed } > 1
                        if (multiTouch || scale > 1f) {
                            val newScale = (scale * event.calculateZoom()).coerceIn(1f, 6f)
                            offset = if (newScale > 1f) clamp(offset + event.calculatePan(), newScale, size.width, size.height) else Offset.Zero
                            if (newScale != scale) {
                                scale = newScale
                                onZoomChanged(scale > 1f)
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(file) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            val center = Offset(size.width / 2f, size.height / 2f)
                            offset = clamp((center - tap) * 1.5f, scale, size.width, size.height)
                        }
                        onZoomChanged(scale > 1f)
                    },
                )
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}

