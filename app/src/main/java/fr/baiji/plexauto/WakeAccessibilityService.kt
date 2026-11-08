package fr.baiji.plexauto

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * v1.6.0 : service d'accessibilité « vide ».
 *
 * Pourquoi ? Depuis Android 10, une appli en arrière-plan n'a pas le droit d'ouvrir un écran,
 * sauf exceptions. Nous utilisions la permission « afficher par-dessus les autres applis »,
 * mais la TV Philips la RETIRE à chaque démarrage complet (veille profonde).
 * Autre exception prévue par Android : avoir un service d'accessibilité actif. Ce réglage-là
 * est conservé aux redémarrages (c'est ce qu'utilise Projectivy).
 *
 * Ce service ne lit rien (voir res/xml/accessibility_config.xml) et ne réagit à aucun événement.
 * Bonus : Android le démarre tout seul à chaque démarrage de la TV, ce qui relance aussi
 * la surveillance si jamais l'événement « démarrage terminé » n'arrivait pas.
 */
class WakeAccessibilityService : AccessibilityService() {

    /** Appelé quand Android active le service (au démarrage de la TV, ou après activation). */
    override fun onServiceConnected() {
        super.onServiceConnected()
        EventLog.add(this, "Service d'accessibilité actif (ouverture autorisée)")
        if (Prefs.isEnabled(this)) WatchService.start(this, launchAfterBoot = false)
    }

    // Aucun événement à traiter : le service n'est là que pour l'autorisation.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        /** Nom complet du service, tel qu'Android l'écrit dans ses réglages. */
        fun componentName(context: Context) =
            ComponentName(context, WakeAccessibilityService::class.java).flattenToString()

        /** Le service est-il activé dans les réglages d'accessibilité de la TV ? */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            // La liste est du type "appli1/.Service1:appli2/appli2.Service2" (forme courte ou longue) :
            // unflattenFromString comprend les deux, on compare donc des ComponentName.
            val ours = ComponentName(context, WakeAccessibilityService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it.trim()) == ours }
        }
    }
}
