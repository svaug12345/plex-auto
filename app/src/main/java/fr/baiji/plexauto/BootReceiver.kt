package fr.baiji.plexauto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Récepteur déclaré dans le manifeste : Android le réveille même si l'appli ne tourne pas.
 *
 * Deux événements nous intéressent :
 *  - BOOT_COMPLETED      : la TV vient de VRAIMENT démarrer (après une coupure de courant,
 *                          un redémarrage, une mise à jour système…) → on lance Plex.
 *  - MY_PACKAGE_REPLACED : on vient d'installer une nouvelle version de Plex Auto
 *                          → on relance juste le service, sans ouvrir Plex.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs.isEnabled(context)) return // automatisme désactivé : on ne fait rien

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                EventLog.add(context, "Démarrage complet de la TV détecté")
                WatchService.start(context, launchAfterBoot = true)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // v1.5.1 : on note si la mise à jour a fait sauter la permission « par-dessus »
                val overlayOk = android.provider.Settings.canDrawOverlays(context)
                EventLog.add(
                    context,
                    "Appli mise à jour → surveillance relancée" +
                        (if (overlayOk) " (permission par-dessus OK)" else " ⛔ permission par-dessus PERDUE")
                )
                WatchService.start(context, launchAfterBoot = false)
            }
        }
    }
}
