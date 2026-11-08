package com.scalari.fichiers.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.scalari.fichiers.BuildConfig
import com.scalari.fichiers.MainActivity
import com.scalari.fichiers.ui.FileOpener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

data class Release(val version: String, val apkUrl: String, val apkSize: Long, val pageUrl: String, val notes: String)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    data class Error(val message: String, val release: Release? = null) : UpdateState
}

/**
 * Mises à jour publiées sur GitHub : vérification (API « releases/latest »), téléchargement de
 * l'APK puis installation. Seul github.com est contacté ; aucune donnée n'est envoyée.
 */
object UpdateManager {
    private const val REPO = "scalariapp-byte/Gestionnaire-de-fichiers-android"
    private const val PREFS = "mises_a_jour"
    private const val CHANNEL = "mises_a_jour"
    private const val WORK = "verification_mises_a_jour"

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isAutoCheckEnabled(c: Context) = prefs(c).getBoolean("auto", true)

    fun setAutoCheck(c: Context, enabled: Boolean) {
        prefs(c).edit().putBoolean("auto", enabled).apply()
        schedule(c)
    }

    /** Planifie (ou annule) la vérification quotidienne en arrière-plan. */
    fun schedule(c: Context) {
        val wm = WorkManager.getInstance(c)
        if (!isAutoCheckEnabled(c)) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Compare « v1.10.0 » et « 1.9.2 » numériquement. */
    fun isNewer(remote: String, local: String = BuildConfig.VERSION_NAME): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").split('.', '-', '+').map { it.toIntOrNull() ?: 0 }
        val a = parts(remote)
        val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Dernière version publiée, ou null en cas d'échec réseau. */
    suspend fun fetchLatest(): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "MesFichiers-Android/${BuildConfig.VERSION_NAME}")
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            try {
                if (conn.responseCode != 200) return@runCatching null
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                val assets = json.getJSONArray("assets")
                var apkUrl = ""
                var size = 0L
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    val name = a.getString("name")
                    if (name.endsWith(".apk") && !name.contains("debug")) {
                        apkUrl = a.getString("browser_download_url")
                        size = a.optLong("size")
                    }
                }
                if (apkUrl.isEmpty()) return@runCatching null
                Release(json.getString("tag_name"), apkUrl, size, json.optString("html_url"), json.optString("body"))
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    /**
     * Vérifie s'il y a une mise à jour. [force] ignore le délai minimal entre deux vérifications
     * (6 h) ; [manual] signale « à jour » ou les erreurs, sinon la vérification reste discrète.
     */
    suspend fun check(c: Context, force: Boolean = false, manual: Boolean = false) {
        val p = prefs(c)
        val now = System.currentTimeMillis()
        if (!force && now - p.getLong("last_check", 0) < 6 * 3600_000L) {
            p.getString("available", null)?.let { cached ->
                restore(cached)?.takeIf { isNewer(it.version) }?.let { _state.value = UpdateState.Available(it) }
            }
            return
        }
        if (manual) _state.value = UpdateState.Checking
        val latest = fetchLatest()
        if (latest == null) {
            if (manual) _state.value = UpdateState.Error("Impossible de joindre GitHub. Vérifiez la connexion.")
            return
        }
        p.edit().putLong("last_check", now).apply()
        if (isNewer(latest.version)) {
            p.edit().putString("available", save(latest)).apply()
            _state.value = UpdateState.Available(latest)
        } else {
            p.edit().remove("available").apply()
            _state.value = if (manual) UpdateState.UpToDate else UpdateState.Idle
        }
    }

    fun dismiss(c: Context) {
        (_state.value as? UpdateState.Available)?.let { prefs(c).edit().putString("dismissed", it.release.version).apply() }
        _state.value = UpdateState.Idle
    }

    fun isDismissed(c: Context, version: String) = prefs(c).getString("dismissed", null) == version

    private fun save(r: Release) = JSONObject()
        .put("v", r.version).put("u", r.apkUrl).put("s", r.apkSize).put("p", r.pageUrl).put("n", r.notes).toString()

    private fun restore(s: String) = runCatching {
        val o = JSONObject(s)
        Release(o.getString("v"), o.getString("u"), o.optLong("s"), o.optString("p"), o.optString("n"))
    }.getOrNull()

    /** Télécharge l'APK de [release] puis lance l'installation. */
    suspend fun downloadAndInstall(c: Context, release: Release) {
        val ctx = c.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ctx.packageManager.canRequestPackageInstalls()) {
            // Android demande d'autoriser l'installation d'applis depuis « Mes fichiers ».
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            _state.value = UpdateState.Error("Autorisez l'installation depuis « Mes fichiers », puis touchez Installer à nouveau.", release)
            return
        }
        _state.value = UpdateState.Downloading(release, 0f)
        val file = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(ctx.cacheDir, "mises_a_jour").apply { deleteRecursively(); mkdirs() }
                val out = File(dir, "Mes-fichiers-${release.version}.apk")
                var conn = URL(release.apkUrl).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "MesFichiers-Android/${BuildConfig.VERSION_NAME}")
                // GitHub redirige vers son stockage : suivre manuellement si le protocole change.
                var redirects = 0
                while (conn.responseCode in 301..308 && redirects++ < 5) {
                    val next = conn.getHeaderField("Location")
                    conn.disconnect()
                    conn = URL(next).openConnection() as HttpURLConnection
                }
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
                conn.inputStream.use { input ->
                    out.outputStream().use { o ->
                        val buf = ByteArray(256 * 1024)
                        var done = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            o.write(buf, 0, n)
                            done += n
                            if (total > 0) _state.value = UpdateState.Downloading(release, (done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                conn.disconnect()
                out
            }.getOrNull()
        }
        if (file == null || file.length() == 0L) {
            _state.value = UpdateState.Error("Échec du téléchargement.", release)
            return
        }
        _state.value = UpdateState.Available(release)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(FileOpener.uriFor(ctx, file), "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(intent) }
            .onFailure { _state.value = UpdateState.Error("Impossible de lancer l'installation.", release) }
    }

    /** Notification de nouvelle version, une seule fois par version. */
    @android.annotation.SuppressLint("MissingPermission")
    fun notifyIfNew(c: Context, release: Release) {
        val p = prefs(c)
        if (p.getString("notified", null) == release.version) return
        val nm = NotificationManagerCompat.from(c)
        if (!nm.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            c.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL, "Mises à jour", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val open = PendingIntent.getActivity(
            c, 1, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Mise à jour disponible : ${release.version}")
            .setContentText("Touchez pour l'installer depuis « Mes fichiers ».")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(42, n) }
        p.edit().putString("notified", release.version).apply()
    }
}

/** Vérification quotidienne en arrière-plan. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        UpdateManager.check(applicationContext, force = true)
        (UpdateManager.state.value as? UpdateState.Available)?.let { UpdateManager.notifyIfNew(applicationContext, it.release) }
        return Result.success()
    }
}
