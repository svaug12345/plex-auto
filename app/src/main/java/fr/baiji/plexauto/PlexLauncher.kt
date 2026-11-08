package fr.baiji.plexauto

import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Tout ce qui concerne "ouvrir la cible" (Plex ou le dashboard) est regroupé ici,
 * pour que le service ET l'écran de réglages utilisent exactement le même code.
 */
object PlexLauncher {

    private const val TAG = "PlexAuto" // étiquette visible dans les logs : adb logcat -s PlexAuto

    /** Plex est-il installé sur la TV ? */
    fun isInstalled(context: Context): Boolean = buildPlexIntent(context) != null

    /**
     * Construit l'"Intent" (= la demande d'ouverture) qui démarre Plex.
     * Sur TV, on prend en priorité le point d'entrée "Leanback" (version TV de l'appli) ;
     * à défaut, le point d'entrée classique (version téléphone).
     * Renvoie null si Plex n'est pas installé.
     */
    private fun buildPlexIntent(context: Context): Intent? {
        val pm = context.packageManager
        return pm.getLeanbackLaunchIntentForPackage(Prefs.PLEX_PACKAGE)
            ?: pm.getLaunchIntentForPackage(Prefs.PLEX_PACKAGE)
    }

    /**
     * Ouvre la cible choisie dans les réglages. Renvoie true si la demande a été envoyée.
     *
     * Remarque : depuis un service en arrière-plan, Android bloque silencieusement
     * l'ouverture si l'appli n'a pas la permission "afficher par-dessus les autres applis".
     * C'est vrai pour Plex comme pour notre propre écran Dashboard. D'où la commande ADB du README.
     */
    fun launchTarget(context: Context): Boolean {
        // 1.2.0 : pendant le créneau du rappel, on pose d'abord la question des tâches.
        // C'est cet écran qui proposera ensuite Plex ou le dashboard.
        if (Prefs.isReminderEnabled(context) && Prefs.isInReminderWindow()) {
            return launchTaskCheck(context)
        }
        return when (Prefs.target(context)) {
            Prefs.Target.PLEX -> launchPlex(context)
            Prefs.Target.DASHBOARD -> launchDashboard(context)
        }
    }

    /** Ouvre l'écran « Est-ce que les tâches sont faites ? ». */
    fun launchTaskCheck(context: Context): Boolean {
        val intent = Intent(context, TaskCheckActivity::class.java)
            // Même logique que le dashboard : une seule question à l'écran, jamais deux empilées
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        return start(context, intent, "Question des tâches")
    }

    /**
     * v1.4.3 : la prochaine ouverture concerne-t-elle UN DE NOS écrans (rappel ou dashboard) ?
     * Seuls ceux-là peuvent être « relancés » : on ne sait pas savoir si Plex est à l'écran,
     * et le rouvrir pendant un film le ramènerait à son accueil.
     */
    fun ownScreenWanted(context: Context): Boolean =
        (Prefs.isReminderEnabled(context) && Prefs.isInReminderWindow()) ||
            Prefs.target(context) == Prefs.Target.DASHBOARD

    /** Ouvre Plex. */
    fun launchPlex(context: Context): Boolean {
        val intent = buildPlexIntent(context) ?: run {
            Log.w(TAG, "Plex n'est pas installé")
            return false
        }
        // NEW_TASK : obligatoire quand on ouvre une activité depuis autre chose qu'un écran.
        // RESET_TASK_IF_NEEDED : ramène Plex dans un état propre s'il était déjà ouvert.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return start(context, intent, "Plex")
    }

    /** Ouvre notre écran plein écran qui affiche le dashboard famille. */
    fun launchDashboard(context: Context): Boolean {
        val intent = Intent(context, DashboardActivity::class.java)
            // CLEAR_TOP + SINGLE_TOP : si le dashboard est déjà ouvert, on le réutilise
            // (il est rechargé via onNewIntent) au lieu d'en empiler un deuxième.
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        return start(context, intent, "Dashboard")
    }

    /**
     * v1.4.0 : ouvre l'écran d'accueil de la TV (Google TV, ou Projectivy s'il est l'accueil),
     * exactement comme le bouton Maison de la télécommande.
     */
    fun launchHome(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME) // "HOME" = l'appli qui sert d'écran d'accueil
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(context, intent, "Accueil TV")
    }

    /** Envoie la demande d'ouverture au système, en attrapant les erreurs pour ne jamais planter. */
    private fun start(context: Context, intent: Intent, label: String): Boolean = try {
        context.startActivity(intent)
        EventLog.add(context, "$label : ouverture demandée")
        true
    } catch (e: Exception) {
        Log.e(TAG, "Impossible de lancer $label", e)
        EventLog.add(context, "ÉCHEC ouverture $label : ${e.message}")
        false
    }
}
