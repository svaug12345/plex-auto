package fr.baiji.plexauto

import android.os.SystemClock

/**
 * v1.4.3 : ce que le service doit savoir de nos écrans pour décider d'une « relance ».
 *
 * Le problème : après un démarrage complet, le menu Google TV finit de se charger APRÈS
 * l'ouverture du rappel et passe par-dessus. Le service revérifie donc un peu plus tard :
 *   - si notre écran (rappel ou dashboard) n'est plus visible,
 *   - et que personne ne l'a quitté volontairement (réponse, bouton Accueil…),
 *   → il le remet devant.
 *
 * @Volatile : ces variables sont lues par le service et écrites par les écrans ;
 * @Volatile garantit que chacun voit la dernière valeur écrite.
 */
object ScreenState {

    /** true quand le rappel ou le dashboard est au premier plan. */
    @Volatile var ownScreenShowing = false

    /** Moment (horloge depuis le démarrage) où quelqu'un a quitté volontairement notre écran. */
    @Volatile var userLeftAt = 0L

    /** À appeler quand l'utilisateur répond ou appuie sur Accueil. */
    fun markUserLeft() {
        userLeftAt = SystemClock.elapsedRealtime()
    }
}
