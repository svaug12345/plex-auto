package fr.baiji.plexauto

import android.content.Context
import android.util.Log
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * v1.4.1 : petit journal des derniers événements, gardé DANS l'appli.
 *
 * Pourquoi ? Le journal système (logcat) de la TV se remplit très vite : quelques minutes
 * après un rallumage raté, nos lignes ont déjà disparu. Ici, on garde les 20 dernières lignes
 * dans les SharedPreferences (elles survivent aux redémarrages) et l'écran de réglages les affiche.
 */
object EventLog {

    private const val FILE = "plexauto_events"
    private const val KEY = "lines"
    private const val MAX_LINES = 20

    // Format court : "08/11 16:42:05"
    private val FORMAT = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss")

    /**
     * Ajoute une ligne horodatée (et l'écrit aussi dans logcat, étiquette PlexAuto).
     * @Synchronized : le service et le récepteur peuvent écrire en même temps ; on les fait
     * passer l'un après l'autre pour ne pas perdre de ligne.
     */
    @Synchronized
    fun add(context: Context, message: String) {
        Log.i("PlexAuto", message)
        val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val lines = sp.getString(KEY, "")!!.lines().filter { it.isNotBlank() }.toMutableList()
        lines.add("${LocalDateTime.now().format(FORMAT)}  $message")
        // On ne garde que les MAX_LINES dernières (takeLast = les plus récentes)
        sp.edit().putString(KEY, lines.takeLast(MAX_LINES).joinToString("\n")).apply()
    }

    /** Les lignes enregistrées, de la plus récente à la plus ancienne. */
    fun read(context: Context): List<String> =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY, "")!!.lines().filter { it.isNotBlank() }.reversed()
}
