package com.scalari.fichiers.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BootApp(
    val packageName: String,
    val label: String,
    val icon: Bitmap?,
    val isSystem: Boolean,
    /** Arrêtée (« forcer l'arrêt ») : ne recevra pas l'événement de démarrage avant d'être rouverte. */
    val isStopped: Boolean,
    /** Application désactivée dans les réglages. */
    val isDisabled: Boolean,
    val receivers: Int,
    /** Récepteurs de démarrage désactivés (blocage fait via Isolateur). */
    val isBlocked: Boolean = false,
)

/** Applications qui demandent à être lancées au démarrage du téléphone. */
object BootApps {

    private val bootActions = listOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_LOCKED_BOOT_COMPLETED,
        "android.intent.action.QUICKBOOT_POWERON",
        "com.htc.intent.action.QUICKBOOT_POWERON",
    )

    @Suppress("DEPRECATION")
    suspend fun list(context: Context): List<BootApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_DIRECT_BOOT_AWARE or
            PackageManager.MATCH_DIRECT_BOOT_UNAWARE
        val receiversByPackage = HashMap<String, MutableSet<String>>()
        for (action in bootActions) {
            runCatching { pm.queryBroadcastReceivers(Intent(action), flags) }.getOrDefault(emptyList()).forEach { ri ->
                val ai = ri.activityInfo ?: return@forEach
                receiversByPackage.getOrPut(ai.packageName) { HashSet() } += ai.name
            }
        }
        receiversByPackage.mapNotNull { (pkg, receivers) ->
            if (pkg == context.packageName) return@mapNotNull null
            val ai = runCatching { pm.getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS) }.getOrNull()
                ?: return@mapNotNull null
            BootApp(
                packageName = pkg,
                label = runCatching { ai.loadLabel(pm).toString() }.getOrDefault(pkg),
                icon = runCatching { ai.loadIcon(pm).toBitmap(96, 96) }.getOrNull(),
                isSystem = ai.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                isStopped = ai.flags and ApplicationInfo.FLAG_STOPPED != 0,
                isDisabled = !ai.enabled,
                receivers = receivers.size,
                isBlocked = receivers.all { cls ->
                    runCatching {
                        pm.getComponentEnabledSetting(android.content.ComponentName(pkg, cls)) ==
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    }.getOrDefault(false)
                },
            )
        }.sortedWith(compareBy<BootApp> { it.isSystem }.thenBy { it.label.lowercase() })
    }
}
