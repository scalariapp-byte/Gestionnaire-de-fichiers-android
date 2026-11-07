package com.scalari.fichiers.data

import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class Category(val label: String) {
    IMAGES("Images"),
    VIDEOS("Vidéos"),
    AUDIO("Audio"),
    DOCUMENTS("Documents"),
    ARCHIVES("Archives"),
    APKS("APK"),
    LARGE("Gros fichiers"),
    RECENT("Récents"),
}

object MediaQueries {

    private const val LARGE_THRESHOLD = 50L * 1000 * 1000

    @Suppress("DEPRECATION")
    suspend fun query(
        context: Context,
        category: Category,
        showHidden: Boolean,
        limit: Int = 5000,
    ): List<FileItem> = withContext(Dispatchers.IO) {
        val uri = MediaStore.Files.getContentUri("external")
        val col = MediaStore.Files.FileColumns.MEDIA_TYPE
        val data = MediaStore.MediaColumns.DATA
        val size = MediaStore.MediaColumns.SIZE
        val date = MediaStore.MediaColumns.DATE_MODIFIED
        val mime = MediaStore.MediaColumns.MIME_TYPE

        fun byExt(exts: Collection<String>): Pair<String, Array<String>> =
            exts.joinToString(" OR ", "(", ")") { "$data LIKE ?" } to exts.map { "%.$it" }.toTypedArray()

        val (selection, args) = when (category) {
            Category.IMAGES -> "$col=?" to arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString())
            Category.VIDEOS -> "$col=?" to arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
            Category.AUDIO -> "$col=?" to arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO.toString())
            Category.DOCUMENTS -> byExt(FileTypes.documentExtensions)
            Category.ARCHIVES -> byExt(FileTypes.archiveExtensions)
            Category.APKS -> byExt(listOf("apk", "apks", "xapk"))
            Category.LARGE -> "$size>?" to arrayOf(LARGE_THRESHOLD.toString())
            Category.RECENT -> {
                val since = (System.currentTimeMillis() - 30L * 24 * 3600 * 1000) / 1000
                "$mime IS NOT NULL AND $date>?" to arrayOf(since.toString())
            }
        }
        val sort = if (category == Category.LARGE) "$size DESC" else "$date DESC"
        val max = if (category == Category.RECENT) minOf(limit, 200) else limit

        val out = ArrayList<FileItem>()
        runCatching {
            context.contentResolver.query(uri, arrayOf(data, size, date), selection, args, sort)?.use { c ->
                val iData = c.getColumnIndexOrThrow(data)
                val iSize = c.getColumnIndexOrThrow(size)
                val iDate = c.getColumnIndexOrThrow(date)
                while (c.moveToNext() && out.size < max) {
                    val path = c.getString(iData) ?: continue
                    if (path.contains("/${TrashManager.TRASH_DIR}/")) continue
                    val hidden = path.contains("/.")
                    if (hidden && !showHidden) continue
                    val f = File(path)
                    if (!f.isFile) continue
                    out += FileItem(
                        file = f,
                        name = f.name,
                        isDirectory = false,
                        size = c.getLong(iSize).takeIf { it > 0 } ?: f.length(),
                        lastModified = c.getLong(iDate) * 1000,
                        isHidden = hidden,
                        childCount = -1,
                        type = FileTypes.of(f.name, false),
                    )
                }
            }
        }
        out
    }
}
