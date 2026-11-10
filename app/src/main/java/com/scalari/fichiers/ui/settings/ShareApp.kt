package com.scalari.fichiers.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Lien de téléchargement : page de la dernière version publiée sur GitHub. */
const val APP_DOWNLOAD_URL = "https://github.com/scalariapp-byte/Gestionnaire-de-fichiers-android/releases/latest"

object ShareApp {
    private const val MESSAGE = "Télécharge « Mes fichiers », un gestionnaire de fichiers pour Android : $APP_DOWNLOAD_URL"

    fun share(context: Context) {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, MESSAGE)
        runCatching { context.startActivity(Intent.createChooser(intent, "Partager l'application")) }
    }

    fun copy(context: Context) {
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("Mes fichiers", APP_DOWNLOAD_URL))
    }

    /** QR code du lien (noir sur blanc, correction d'erreur moyenne). */
    fun qrCode(size: Int = 600): Bitmap {
        val matrix = QRCodeWriter().encode(
            APP_DOWNLOAD_URL, BarcodeFormat.QR_CODE, size, size,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1),
        )
        val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) AndroidColor.BLACK else AndroidColor.WHITE }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }
}

/** Tout le partage au même endroit : QR code, lien, envoi et copie. */
@Composable
fun ShareAppDialog(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Partager Mes fichiers") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                QrCodeContent()
                androidx.compose.material3.Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        APP_DOWNLOAD_URL.removePrefix("https://"),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp),
                    )
                }
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            ShareApp.copy(context)
                            android.widget.Toast.makeText(context, "Lien copié", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Copier") }
                    androidx.compose.material3.Button(
                        onClick = { ShareApp.share(context) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Envoyer") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

@Composable
fun QrCodeDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Télécharger Mes fichiers") },
        text = { QrCodeContent() },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

@Composable
fun QrCodeContent() {
    val bitmap = remember { ShareApp.qrCode() }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .background(Color.White, MaterialTheme.shapes.medium)
                        .padding(12.dp)
                ) {
                    Image(bitmap.asImageBitmap(), "QR code du lien de téléchargement", Modifier.size(220.dp), filterQuality = FilterQuality.None)
                }
                Text(
                    "Scannez ce code avec l'appareil photo d'un autre téléphone pour ouvrir la page de téléchargement.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
}
