@file:OptIn(ExperimentalFoundationApi::class)

package com.scalari.fichiers.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.scalari.fichiers.data.FileItem
import com.scalari.fichiers.data.FileType
import com.scalari.fichiers.data.formatDate
import com.scalari.fichiers.data.formatSize

@Suppress("DEPRECATION")
fun FileType.icon(): ImageVector = when (this) {
    FileType.FOLDER -> Icons.Filled.Folder
    FileType.IMAGE -> Icons.Filled.Image
    FileType.VIDEO -> Icons.Filled.Movie
    FileType.AUDIO -> Icons.Filled.AudioFile
    FileType.PDF -> Icons.Filled.PictureAsPdf
    FileType.DOCUMENT -> Icons.Filled.Description
    FileType.SPREADSHEET -> Icons.Filled.TableChart
    FileType.PRESENTATION -> Icons.Filled.Slideshow
    FileType.TEXT -> Icons.Filled.TextFields
    FileType.CODE -> Icons.Filled.Code
    FileType.ARCHIVE -> Icons.Filled.FolderZip
    FileType.APK -> Icons.Filled.Android
    FileType.OTHER -> Icons.Filled.InsertDriveFile
}

@Composable
fun FileType.tint(): Color = when (this) {
    FileType.FOLDER -> MaterialTheme.colorScheme.primary
    FileType.IMAGE -> Color(0xFF43A047)
    FileType.VIDEO -> Color(0xFFE53935)
    FileType.AUDIO -> Color(0xFF8E24AA)
    FileType.PDF -> Color(0xFFD32F2F)
    FileType.DOCUMENT -> Color(0xFF1E88E5)
    FileType.SPREADSHEET -> Color(0xFF2E7D32)
    FileType.PRESENTATION -> Color(0xFFEF6C00)
    FileType.TEXT -> Color(0xFF607D8B)
    FileType.CODE -> Color(0xFF00897B)
    FileType.ARCHIVE -> Color(0xFF8D6E63)
    FileType.APK -> Color(0xFF7CB342)
    FileType.OTHER -> Color(0xFF757575)
}

@Composable
fun FileThumbnail(item: FileItem, modifier: Modifier = Modifier, iconSize: Int = 26) {
    val color = item.type.tint()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(item.type.icon(), contentDescription = null, tint = color, modifier = Modifier.size(iconSize.dp))
        if (item.type == FileType.IMAGE || item.type == FileType.VIDEO) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.file)
                    .size(256)
                    .memoryCacheKey("${item.path}:${item.lastModified}")
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (item.type == FileType.VIDEO) {
                Icon(
                    Icons.Filled.PlayCircle,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size((iconSize * 0.8f).dp),
                )
            }
        }
    }
}

fun FileItem.subtitle(): String =
    if (isDirectory && childCount < 0) formatDate(lastModified)
    else if (isDirectory) "${childCount} élément${if (childCount > 1) "s" else ""} · ${formatDate(lastModified)}"
    else "${formatSize(size)} · ${formatDate(lastModified)}"

@Composable
fun FileRow(
    item: FileItem,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String = item.subtitle(),
) {
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    ListItem(
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = ListItemDefaults.colors(containerColor = bg),
        leadingContent = {
            Box(contentAlignment = Alignment.Center) {
                FileThumbnail(item, Modifier.size(46.dp))
                if (selected) SelectedBadge(Modifier.align(Alignment.BottomEnd))
            }
        },
        headlineContent = {
            Text(
                item.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (item.isHidden) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
            )
        },
        supportingContent = { Text(supporting, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}

@Composable
private fun SelectedBadge(modifier: Modifier = Modifier) {
    Icon(
        Icons.Filled.CheckCircle,
        contentDescription = "Sélectionné",
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .size(20.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape),
    )
}

@Composable
fun FileGridItem(
    item: FileItem,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .padding(4.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow
        ),
    ) {
        Column(Modifier.padding(6.dp)) {
            Box {
                FileThumbnail(
                    item,
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    iconSize = 44,
                )
                if (selected) SelectedBadge(Modifier.align(Alignment.TopEnd).padding(4.dp))
            }
            Text(
                item.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
        }
    }
}
