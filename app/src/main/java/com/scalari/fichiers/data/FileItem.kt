package com.scalari.fichiers.data

import android.webkit.MimeTypeMap
import androidx.compose.runtime.Immutable
import java.io.File
import java.util.Date
import java.util.Locale

enum class FileType {
    FOLDER, IMAGE, VIDEO, AUDIO, PDF, DOCUMENT, SPREADSHEET, PRESENTATION, TEXT, CODE, ARCHIVE, APK, OTHER
}

@Immutable
data class FileItem(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val isHidden: Boolean,
    /** Nombre d'enfants pour un dossier (-2 = pas encore calculé), -1 pour un fichier. */
    val childCount: Int,
    val type: FileType,
) {
    val path: String get() = file.absolutePath
    val extension: String get() = file.extension.lowercase(Locale.ROOT)
}

fun File.toFileItem(countChildren: Boolean = true): FileItem {
    val dir = isDirectory
    return FileItem(
        file = this,
        name = name,
        isDirectory = dir,
        size = if (dir) 0L else length(),
        lastModified = lastModified(),
        isHidden = name.startsWith("."),
        childCount = if (!dir) -1 else if (countChildren) (list()?.size ?: 0) else -2,
        type = FileTypes.of(name, dir),
    )
}

object FileTypes {
    private val image = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "ico")
    private val video = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "flv", "wmv", "ts", "mpg", "mpeg")
    private val audio = setOf("mp3", "wav", "ogg", "flac", "m4a", "aac", "opus", "wma", "amr", "mid", "midi")
    private val document = setOf("doc", "docx", "odt", "rtf", "epub")
    private val spreadsheet = setOf("xls", "xlsx", "ods", "csv", "tsv")
    private val presentation = setOf("ppt", "pptx", "odp")
    private val text = setOf("txt", "md", "log", "ini", "cfg", "conf", "properties", "nfo", "srt", "vtt")
    private val code = setOf(
        "kt", "kts", "java", "py", "js", "ts", "jsx", "tsx", "html", "htm", "css", "xml", "json", "yml",
        "yaml", "toml", "sh", "bat", "c", "cpp", "h", "hpp", "cs", "rs", "go", "rb", "php", "sql", "gradle", "lua", "swift"
    )
    private val archive = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "jar")

    val documentExtensions = document + spreadsheet + presentation + setOf("pdf", "txt", "md")
    val archiveExtensions = archive

    fun of(name: String, isDirectory: Boolean): FileType {
        if (isDirectory) return FileType.FOLDER
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (ext) {
            in image -> FileType.IMAGE
            in video -> FileType.VIDEO
            in audio -> FileType.AUDIO
            "pdf" -> FileType.PDF
            in document -> FileType.DOCUMENT
            in spreadsheet -> FileType.SPREADSHEET
            in presentation -> FileType.PRESENTATION
            in text -> FileType.TEXT
            in code -> FileType.CODE
            in archive -> FileType.ARCHIVE
            "apk", "apks", "xapk" -> FileType.APK
            else -> FileType.OTHER
        }
    }

    /** Fichiers ouverts dans l'éditeur texte intégré. */
    fun isTextEditable(item: FileItem): Boolean =
        item.type == FileType.TEXT || item.type == FileType.CODE || item.extension == "csv" || item.extension == "tsv"
}

fun mimeTypeOf(file: File): String {
    val ext = file.extension.lowercase(Locale.ROOT)
    if (ext == "apk") return "application/vnd.android.package-archive"
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
}

fun formatSize(bytes: Long): String {
    if (bytes < 1000) return "$bytes o"
    val units = arrayOf("Ko", "Mo", "Go", "To", "Po")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1000 && unit < units.size - 1) {
        value /= 1000
        unit++
    }
    return if (value >= 100) String.format(Locale.FRANCE, "%.0f %s", value, units[unit])
    else String.format(Locale.FRANCE, "%.1f %s", value, units[unit])
}

private val timeFmt = java.text.SimpleDateFormat("HH:mm", Locale.FRANCE)
private val dayFmt = java.text.SimpleDateFormat("d MMM", Locale.FRANCE)
private val yearFmt = java.text.SimpleDateFormat("d MMM yyyy", Locale.FRANCE)

/** « Aujourd'hui, 14:32 », « Hier, 09:10 », « 5 nov., 18:00 », « 30 oct. 2024 ». */
fun formatDate(millis: Long): String {
    val cal = java.util.Calendar.getInstance()
    val today = cal.get(java.util.Calendar.DAY_OF_YEAR) to cal.get(java.util.Calendar.YEAR)
    cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
    val yesterday = cal.get(java.util.Calendar.DAY_OF_YEAR) to cal.get(java.util.Calendar.YEAR)
    cal.timeInMillis = millis
    val day = cal.get(java.util.Calendar.DAY_OF_YEAR) to cal.get(java.util.Calendar.YEAR)
    val date = Date(millis)
    return synchronized(timeFmt) {
        when {
            day == today -> "Aujourd'hui, ${timeFmt.format(date)}"
            day == yesterday -> "Hier, ${timeFmt.format(date)}"
            day.second == today.second -> "${dayFmt.format(date)}, ${timeFmt.format(date)}"
            else -> yearFmt.format(date)
        }
    }
}

/** Tri « naturel » : fichier2 < fichier10, insensible à la casse. */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                val si = i
                while (i < a.length && a[i].isDigit()) i++
                val sj = j
                while (j < b.length && b[j].isDigit()) j++
                val na = a.substring(si, i).trimStart('0')
                val nb = b.substring(sj, j).trimStart('0')
                if (na.length != nb.length) return na.length - nb.length
                val c = na.compareTo(nb)
                if (c != 0) return c
            } else {
                val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (c != 0) return c
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}
