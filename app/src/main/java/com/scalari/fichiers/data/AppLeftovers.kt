package com.scalari.fichiers.data

import android.content.Context
import android.content.pm.PackageManager
import java.io.File

/**
 * Résidus d'applications désinstallées : dossiers laissés sur le stockage partagé par des
 * applis qui ne sont plus installées.
 *
 * Deux niveaux de certitude :
 * - **certain** : dossier privé d'appli (`Android/data`, `Android/obb`, `Android/media`) dont le
 *   paquet n'est pas installé ;
 * - **probable** : dossier à la racine portant un nom de paquet non installé (ex. `.com.exemple.sdk`),
 *   ou dossier connu d'une appli (ex. `Telegram`, `tencent`) dont aucune version n'est installée.
 *   Il peut contenir des fichiers à garder (photos reçues…) : non coché par défaut.
 *
 * Limite Android 11+ : `Android/data` et `Android/obb` ne sont pas lisibles par les autres applis ;
 * Android y supprime de toute façon les données à la désinstallation.
 */
object AppLeftovers {

    data class Leftover(
        val dir: File,
        val reason: String,
        val sure: Boolean,
        val size: Long,
    )

    /** Nom de paquet Android : au moins deux segments commençant par une lettre. */
    private val PACKAGE = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")

    /** Préfixes de paquets courants : à la racine, évite de prendre « mes.photos » pour un paquet. */
    private val PACKAGE_PREFIXES = setOf(
        "com", "org", "net", "io", "de", "fr", "tv", "me", "co", "app", "info", "ru", "cn", "jp",
        "uk", "us", "br", "in", "it", "es", "nl", "ch", "kr", "pl", "dev", "xyz", "eu", "be", "ca",
        "au", "air", "mobi", "ai", "vn", "id", "tr", "se", "at", "cz", "hu", "pt", "gr", "ir", "mega",
    )

    /** Dossiers créés à la racine par des applis connues → (nom de l'appli, paquets possibles). */
    private val KNOWN_FOLDERS: Map<String, Pair<String, List<String>>> = mapOf(
        "whatsapp" to ("WhatsApp" to listOf("com.whatsapp", "com.whatsapp.w4b")),
        "telegram" to ("Telegram" to listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.telegram.plus", "org.thunderdog.challegram")),
        "tencent" to ("WeChat / QQ" to listOf("com.tencent.mm", "com.tencent.mobileqq", "com.tencent.ig")),
        "snapchat" to ("Snapchat" to listOf("com.snapchat.android")),
        "viber" to ("Viber" to listOf("com.viber.voip")),
        "signal" to ("Signal" to listOf("org.thoughtcrime.securesms")),
        "skype" to ("Skype" to listOf("com.skype.raider")),
        "imo" to ("imo" to listOf("com.imo.android.imoim")),
        "kakaotalk" to ("KakaoTalk" to listOf("com.kakao.talk")),
        "line" to ("LINE" to listOf("jp.naver.line.android")),
        "discord" to ("Discord" to listOf("com.discord")),
        "messenger" to ("Messenger" to listOf("com.facebook.orca")),
        "facebook" to ("Facebook" to listOf("com.facebook.katana", "com.facebook.lite")),
        "shareit" to ("SHAREit" to listOf("com.lenovo.anyshare.gps")),
        "xender" to ("Xender" to listOf("cn.xender")),
        "ucdownloads" to ("UC Browser" to listOf("com.UCMobile.intl", "com.uc.browser.en")),
        "kinemaster" to ("KineMaster" to listOf("com.nexstreaming.app.kinemasterfree", "com.kinemaster.app")),
        "picsart" to ("PicsArt" to listOf("com.picsart.studio")),
        "zedge" to ("Zedge" to listOf("net.zedge.android")),
        "camscanner" to ("CamScanner" to listOf("com.intsig.camscanner")),
        "adm" to ("ADM" to listOf("com.dv.adm", "com.dv.adm.pay")),
        "titaniumbackup" to ("Titanium Backup" to listOf("com.keramidas.TitaniumBackup")),
        "mega" to ("MEGA" to listOf("mega.privacy.android.app")),
        "mxplayer" to ("MX Player" to listOf("com.mxtech.videoplayer.ad", "com.mxtech.videoplayer.pro")),
        "vlc" to ("VLC" to listOf("org.videolan.vlc")),
        "inshot" to ("InShot" to listOf("com.camerasideas.instashot")),
        "vidmate" to ("VidMate" to listOf("com.nemo.vidmate")),
        "snaptube" to ("Snaptube" to listOf("com.snaptube.premium")),
        "tubemate" to ("TubeMate" to listOf("devian.tubemate.v3")),
        "quickpic" to ("QuickPic" to listOf("com.alensw.PicFolder")),
    )

    /** Dossiers standard d'Android : jamais signalés. */
    private val STANDARD = setOf(
        "android", "dcim", "download", "downloads", "pictures", "music", "movies", "documents",
        "alarms", "notifications", "podcasts", "ringtones", "audiobooks", "recordings", "screenshots",
        "lost.dir", ".thumbnails",
    )

    /**
     * Analyse pure (testable sans Android) : [roots] = racines des volumes,
     * [isInstalled] = vrai si le paquet est installé.
     */
    fun scan(roots: List<File>, isInstalled: (String) -> Boolean, sizeOf: (File) -> Long): List<Leftover> {
        val out = ArrayList<Leftover>()
        val seen = HashSet<String>()
        fun add(dir: File, reason: String, sure: Boolean) {
            if (seen.add(dir.absolutePath)) out += Leftover(dir, reason, sure, sizeOf(dir))
        }
        for (root in roots) {
            // Dossiers privés des applis : leur nom est toujours un nom de paquet.
            for (sub in listOf("Android/data", "Android/obb", "Android/media")) {
                File(root, sub).listFiles()?.forEach { d ->
                    if (d.isDirectory && PACKAGE.matches(d.name) && !isInstalled(d.name)) {
                        add(d, "Appli désinstallée : ${d.name}", sure = true)
                    }
                }
            }
            root.listFiles()?.forEach { d ->
                if (!d.isDirectory) return@forEach
                val name = d.name
                val lower = name.lowercase()
                if (lower in STANDARD) return@forEach
                val bare = name.removePrefix(".")
                if (PACKAGE.matches(bare) && bare.substringBefore('.').lowercase() in PACKAGE_PREFIXES) {
                    // À la racine, un même dossier peut servir à une bibliothèque partagée par
                    // plusieurs applis : probable seulement, non coché par défaut.
                    if (!isInstalled(bare)) add(d, "Appli $bare non installée · à vérifier", sure = false)
                    return@forEach
                }
                val known = KNOWN_FOLDERS[lower.removePrefix(".").replace(" ", "")] ?: return@forEach
                if (known.second.none(isInstalled)) {
                    add(d, "${known.first} n'est plus installé · à vérifier", sure = false)
                }
            }
        }
        // Les certains d'abord, puis par taille décroissante.
        return out.sortedWith(compareByDescending<Leftover> { it.sure }.thenByDescending { it.size })
    }

    /** Paquets installés, ou null si la liste est manifestement incomplète (visibilité restreinte). */
    fun installedPackages(context: Context): Set<String>? {
        val pm = context.packageManager
        val set = runCatching { pm.getInstalledPackages(PackageManager.MATCH_DISABLED_COMPONENTS) }
            .getOrNull()?.mapTo(HashSet()) { it.packageName } ?: return null
        // Sans QUERY_ALL_PACKAGES, Android ne renvoie qu'une poignée d'applis : refuser plutôt que
        // de signaler à tort des applis installées comme désinstallées.
        return if (set.size < 15) null else set + context.packageName
    }

    fun scan(context: Context): List<Leftover>? {
        val installed = installedPackages(context) ?: return null
        val pm = context.packageManager
        val isInstalled = { pkg: String ->
            pkg in installed || runCatching { pm.getPackageInfo(pkg, 0); true }.getOrDefault(false)
        }
        val roots = StorageRoots.list(context).map { it.path }
        return scan(roots, isInstalled, FileOps::sizeOf)
    }
}
