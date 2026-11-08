package fr.baiji.plexauto

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * v1.4.0 : mise à jour de l'appli depuis la TV, sans PC ni téléphone.
 *
 * Le principe :
 *  1. À chaque nouvelle version, GitHub Actions compile l'APK et le publie dans une « Release »
 *     du dépôt svaug12345/plex-auto (étiquette "v1.4.0", fichier DemarrageAuto.apk).
 *  2. L'appli demande à l'API GitHub quelle est la dernière Release, compare avec sa propre version,
 *  3. télécharge l'APK et le confie à l'installateur d'Android (PackageInstaller).
 *
 * Android demande une confirmation à l'écran (« Mettre à jour cette appli ? ») : une appli
 * ne peut pas se remplacer en cachette. Sur Android 12+, cette confirmation peut même disparaître
 * une fois que l'appli s'est mise à jour elle-même une première fois.
 *
 * ⚠️ Toutes ces fonctions font du réseau : à appeler HORS du fil principal (dans un Thread).
 */
object Updater {

    private const val TAG = "PlexAuto"

    /** Une version publiée : son numéro (ex. "1.4.0") et l'adresse de téléchargement de l'APK. */
    data class Release(val version: String, val apkUrl: String)

    /** Interroge GitHub. Renvoie null si GitHub ne répond pas ou si la Release n'a pas d'APK. */
    fun fetchLatest(): Release? = try {
        val conn = URL(Prefs.LATEST_RELEASE_API).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        // L'API GitHub exige un "User-Agent" (le nom du programme qui appelle)
        conn.setRequestProperty("User-Agent", "DemarrageAuto")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })

        // "tag_name" vaut par ex. "v1.4.0" → on enlève le "v"
        val version = json.getString("tag_name").removePrefix("v")

        // On cherche, parmi les fichiers de la Release, celui qui finit par ".apk"
        val assets = json.getJSONArray("assets")
        val apk = (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .firstOrNull { it.getString("name").endsWith(".apk") }

        apk?.let { Release(version, it.getString("browser_download_url")) }
    } catch (e: Exception) {
        Log.w(TAG, "Impossible de vérifier les mises à jour", e)
        null
    }

    /**
     * "1.10.0" est-il plus récent que "1.9.2" ? On compare chiffre par chiffre
     * (une simple comparaison de texte dirait le contraire, car "1" < "9").
     */
    fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val l = local.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false // identiques
    }

    /**
     * Télécharge l'APK et lance l'installation. Le résultat (confirmation à afficher, succès,
     * échec) arrive plus tard dans InstallReceiver.
     * @param onProgress appelé avec le pourcentage téléchargé (0..100), pour l'affichage
     */
    fun downloadAndInstall(context: Context, release: Release, onProgress: (Int) -> Unit) {
        val installer = context.packageManager.packageInstaller

        // 1) Une "session" d'installation : un espace où l'on écrit l'APK avant de valider
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ : "si c'est possible, ne demande pas de confirmation"
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)

        installer.openSession(sessionId).use { session ->
            // 2) On télécharge l'APK directement DANS la session (pas de fichier intermédiaire)
            val conn = URL(release.apkUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "DemarrageAuto")
            val total = conn.contentLengthLong // -1 si le serveur ne donne pas la taille

            conn.inputStream.use { input ->
                session.openWrite("update.apk", 0, total).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var lastPct = -1
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                        }
                    }
                    session.fsync(output) // s'assure que tout est bien écrit sur le disque
                }
            }

            // 3) Validation : Android vérifie la signature puis installe.
            //    La réponse sera envoyée à InstallReceiver via ce PendingIntent.
            //    FLAG_MUTABLE (Android 12+) : Android doit pouvoir y ajouter le résultat.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val callback = PendingIntent.getBroadcast(
                context, sessionId, Intent(context, InstallReceiver::class.java), flags
            )
            session.commit(callback.intentSender)
        }
        Log.i(TAG, "Mise à jour ${release.version} envoyée à l'installateur")
    }
}
