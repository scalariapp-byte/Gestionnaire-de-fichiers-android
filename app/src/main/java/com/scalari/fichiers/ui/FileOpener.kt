package com.scalari.fichiers.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.scalari.fichiers.data.mimeTypeOf
import java.io.File

object FileOpener {

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Ouvre le fichier dans une application externe. Retourne false si aucune ne convient. */
    fun openExternal(context: Context, file: File, forceChooser: Boolean = false, mime: String = mimeTypeOf(file)): Boolean {
        val uri = runCatching { uriFor(context, file) }.getOrElse { return false }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        return try {
            context.startActivity(
                if (forceChooser || mime == "*/*") Intent.createChooser(intent, "Ouvrir avec")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                else intent
            )
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    fun share(context: Context, files: List<File>) {
        val shareable = files.filter { it.isFile }
        if (shareable.isEmpty()) return
        val uris = shareable.mapNotNull { runCatching { uriFor(context, it) }.getOrNull() }
        if (uris.isEmpty()) return
        val mimes = shareable.map { mimeTypeOf(it) }.distinct()
        val mime = when {
            mimes.size == 1 -> mimes.first()
            mimes.all { it.substringBefore('/') == mimes.first().substringBefore('/') } -> mimes.first().substringBefore('/') + "/*"
            else -> "*/*"
        }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        intent.type = mime
        intent.clipData = ClipData.newRawUri(null, uris.first()).apply {
            uris.drop(1).forEach { addItem(ClipData.Item(it)) }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { context.startActivity(Intent.createChooser(intent, "Partager")) }
    }
}
