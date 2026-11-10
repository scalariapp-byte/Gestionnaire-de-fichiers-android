package com.scalari.fichiers.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File

/**
 * Échanges avec Google Drive via l'appli Drive du téléphone, sans compte à configurer :
 * - envoi : partage ciblé vers « Enregistrer dans Drive » ;
 * - import : sélecteur de documents Android, où Drive apparaît comme source.
 */
object GoogleDrive {
    const val PACKAGE = "com.google.android.apps.docs"

    fun isInstalled(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(PACKAGE, 0); true }.getOrDefault(false)

    /** Résultat de l'envoi, pour afficher le bon message. */
    enum class Upload { SENT, NO_FILES, NOT_INSTALLED }

    fun upload(context: Context, files: List<File>): Upload {
        val shareable = files.filter { it.isFile }
        if (shareable.isEmpty()) return Upload.NO_FILES
        if (!isInstalled(context)) return Upload.NOT_INSTALLED
        val uris = shareable.mapNotNull { runCatching { FileOpener.uriFor(context, it) }.getOrNull() }
        if (uris.isEmpty()) return Upload.NO_FILES
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        intent.type = if (shareable.size == 1) com.scalari.fichiers.data.mimeTypeOf(shareable.first()) else "*/*"
        intent.clipData = ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.setPackage(PACKAGE)
        return try {
            context.startActivity(intent)
            Upload.SENT
        } catch (e: ActivityNotFoundException) {
            Upload.NOT_INSTALLED
        }
    }

    fun openPlayStore(context: Context) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(market) }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PACKAGE")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}
