package fr.baiji.plexauto

import android.content.Context

/**
 * Petits réglages de l'appli, stockés dans les SharedPreferences
 * (un mini fichier clé/valeur privé à l'appli, qui survit aux redémarrages).
 */
object Prefs {

    /** Identifiant (package) de l'appli Plex sur Android TV / Google TV. */
    const val PLEX_PACKAGE = "com.plexapp.android"

    /** Adresse du dashboard proposée par défaut (modifiable depuis l'écran de réglages).
     *  "?tv" active le mode TV du dashboard : sombre, dense, lisible depuis le canapé. */
    const val DEFAULT_DASHBOARD_URL = "https://baiji.fr/famille/?tv"

    /** Ancienne valeur par défaut (v1.1.0 – v1.2.0), remplacée automatiquement par la nouvelle. */
    private const val OLD_DEFAULT_URL = "https://baiji.fr/"

    /** v1.4.0 : écran de rappel hébergé sur le site. Le modifier sur baiji.fr suffit,
     *  sans mettre à jour l'appli. La copie de l'APK (assets/rappel.html) sert si le Wi-Fi manque. */
    const val REMINDER_URL = "https://baiji.fr/famille/rappel.html"

    /** v1.4.0 : dernière version publiée de l'appli (API GitHub, dépôt public, sans compte). */
    const val LATEST_RELEASE_API = "https://api.github.com/repos/svaug12345/plex-auto/releases/latest"

    /** Google Sheet « Family tasks » publié en TSV : le même lien que le dashboard et le Kindle.
     *  Colonnes : A Mois | B Jour | C Date (JJ/MM/AAAA) | D Qui | E Tâches */
    const val TASKS_TSV_URL =
        "https://docs.google.com/spreadsheets/d/e/2PACX-1vRGC5e1KLZdKKeTUSLC8aqJXHvPphh1ceso4-yYJAUtbyZGPPhriMMtEQpeL4cjcrbQfcp6WOM_RUT2/pub?gid=647675679&single=true&output=tsv"

    /** Délai avant d'ouvrir la cible après un VRAI démarrage : on laisse le temps
     *  au launcher (Google TV ou Projectivy) de s'afficher, sinon il repasserait devant. */
    const val DELAY_AFTER_BOOT_MS = 8_000L

    /** Délai avant d'ouvrir la cible après une SORTIE DE VEILLE (l'écran se rallume). */
    const val DELAY_AFTER_WAKE_MS = 2_000L

    /** Créneau du rappel « les tâches sont faites ? », en minutes depuis minuit.
     *  17h00 = 17*60 ; 19h30 = 19*60+30. Modifiable ici si besoin. */
    const val REMINDER_START_MIN = 17 * 60
    const val REMINDER_END_MIN = 19 * 60 + 30

    /** v1.3.1 : true = le rappel s'affiche à CHAQUE allumage, quelle que soit l'heure
     *  (le créneau ci-dessus est alors ignoré). Repasser à false pour réactiver le créneau. */
    const val REMINDER_ALL_DAY = true

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
    fun dashboardUrl(context: Context): String {
        val saved = sp(context).getString(KEY_URL, null)
        // Si rien n'est enregistré, ou si c'est l'ancienne adresse par défaut, on prend la nouvelle
        return if (saved == null || saved == OLD_DEFAULT_URL) DEFAULT_DASHBOARD_URL else saved
    }

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
        if (REMINDER_ALL_DAY) return true // pas d'horaire pour l'instant : toujours "dans le créneau"
        // java.time est disponible depuis Android 8 (notre minSdk) : pas besoin de Calendar
        val now = java.time.LocalTime.now()
        val minutes = now.hour * 60 + now.minute
        // Début inclus, fin exclue : 17:00 → oui, 19:29 → oui, 19:30 → non
        return minutes in REMINDER_START_MIN until REMINDER_END_MIN
    }

    /** Texte du créneau, pour l'afficher dans les réglages (ex. "17h00 – 19h30"). */
    fun reminderWindowLabel(): String {
        if (REMINDER_ALL_DAY) return "(à chaque allumage)"
        fun fmt(m: Int) = "%dh%02d".format(m / 60, m % 60)
        return "${fmt(REMINDER_START_MIN)} – ${fmt(REMINDER_END_MIN)}"
    }
}
