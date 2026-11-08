package com.scalari.fichiers.data.vault

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import com.scalari.fichiers.data.FileType
import com.scalari.fichiers.data.FileTypes
import java.io.ByteArrayOutputStream
import java.io.File

/** Vignettes JPEG (≈320 px) d'images et de vidéos, pour l'affichage du coffre. */
object Thumbnails {
    private const val SIZE = 320

    fun canThumbnail(name: String): Boolean = FileTypes.of(name, false).let { it == FileType.IMAGE || it == FileType.VIDEO }

    /** Vignette encodée en JPEG, ou null si le fichier n'est pas lisible comme image/vidéo. */
    fun create(file: File, name: String = file.name): ByteArray? = runCatching {
        val bmp = when (FileTypes.of(name, false)) {
            FileType.IMAGE -> image(file)
            FileType.VIDEO -> video(file)
            else -> null
        } ?: return null
        ByteArrayOutputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
            bmp.recycle()
            out.toByteArray()
        }
    }.getOrNull()

    private fun image(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= SIZE && bounds.outHeight / (sample * 2) >= SIZE) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val rotation = runCatching {
            when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        return scale(bmp, rotation)
    }

    private fun video(file: File): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.path)
            r.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { scale(it, 0f) }
        } finally {
            runCatching { r.release() }
        }
    }

    private fun scale(src: Bitmap, rotation: Float): Bitmap {
        val ratio = SIZE.toFloat() / maxOf(src.width, src.height)
        val m = Matrix().apply {
            if (ratio < 1f) postScale(ratio, ratio)
            if (rotation != 0f) postRotate(rotation)
        }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (out !== src) src.recycle()
        return out
    }
}
