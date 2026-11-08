package fr.baiji.plexauto

import android.content.Context

/**
 * Petits réglages de l'appli, stockés dans les SharedPreferences
 * (un mini fichier clé/valeur privé à l'appli, qui survit aux redémarrages).
 */
object Prefs {

    /** Identifiant (package) de l'appli Plex sur Android TV / Google TV. */
    const val PLEX_PACKAGE = "com.plexapp.android"

    /** Adresse du dashboard proposée par défaut (modifiable depuis l'écran de réglages). */
    const val DEFAULT_DASHBOARD_URL = "https://baiji.fr/"

    /** Délai avant d'ouvrir la cible après un VRAI démarrage : on laisse le temps
     *  au launcher (Google TV ou Projectivy) de s'afficher, sinon il repasserait devant. */
    const val DELAY_AFTER_BOOT_MS = 8_000L

    /** Délai avant d'ouvrir la cible après une SORTIE DE VEILLE (l'écran se rallume). */
    const val DELAY_AFTER_WAKE_MS = 2_000L

    /** Créneau du rappel « les tâches sont faites ? », en minutes depuis minuit.
     *  17h00 = 17*60 ; 19h30 = 19*60+30. Modifiable ici si besoin. */
    const val REMINDER_START_MIN = 17 * 60
    const val REMINDER_END_MIN = 19 * 60 + 30

    /** Ce qu'on ouvre automatiquement : Plex ou le dashboard famille. */
    enum class Target { PLEX, DASHBOARD }

    private const val FILE = "plexauto_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_TARGET = "target"
    private const val KEY_URL = "dashboard_url"
    private const val KEY_REMINDER = "task_reminder"

    // Raccourci pour ne pas répéter getSharedPreferences(...) partout
    private fun sp(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** L'automatisme est-il activé ? (oui par défaut) */
    fun isEnabled(context: Context): Boolean = sp(context).getBoolean(KEY_ENABLED, true)

    /** Active ou désactive l'automatisme. apply() écrit en arrière-plan, sans bloquer l'écran. */
    fun setEnabled(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** Cible actuelle. Par défaut PLEX, pour que la mise à jour depuis la 1.0.0 ne change rien. */
    fun target(context: Context): Target =
        // runCatching : si la valeur stockée est illisible, on retombe sur PLEX au lieu de planter
        runCatching { Target.valueOf(sp(context).getString(KEY_TARGET, null) ?: "PLEX") }
            .getOrDefault(Target.PLEX)

    fun setTarget(context: Context, target: Target) {
        sp(context).edit().putString(KEY_TARGET, target.name).apply()
    }

    /** Adresse web du dashboard. */
    fun dashboardUrl(context: Context): String =
        sp(context).getString(KEY_URL, null) ?: DEFAULT_DASHBOARD_URL

    fun setDashboardUrl(context: Context, url: String) {
        sp(context).edit().putString(KEY_URL, url).apply()
    }

    /** Le rappel des tâches est-il activé ? (oui par défaut) */
    fun isReminderEnabled(context: Context): Boolean = sp(context).getBoolean(KEY_REMINDER, true)

    fun setReminderEnabled(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean(KEY_REMINDER, enabled).apply()
    }

    /** Sommes-nous dans le créneau du rappel, à l'heure locale de la TV ? */
    fun isInReminderWindow(): Boolean {
        // java.time est disponible depuis Android 8 (notre minSdk) : pas besoin de Calendar
        val now = java.time.LocalTime.now()
        val minutes = now.hour * 60 + now.minute
        // Début inclus, fin exclue : 17:00 → oui, 19:29 → oui, 19:30 → non
        return minutes in REMINDER_START_MIN until REMINDER_END_MIN
    }

    /** Texte du créneau, pour l'afficher dans les réglages (ex. "17h00 – 19h30"). */
    fun reminderWindowLabel(): String {
        fun fmt(m: Int) = "%dh%02d".format(m / 60, m % 60)
        return "${fmt(REMINDER_START_MIN)} – ${fmt(REMINDER_END_MIN)}"
    }
}
