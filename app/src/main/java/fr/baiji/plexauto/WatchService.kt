package fr.baiji.plexauto

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * Service "de premier plan" qui reste actif en permanence.
 *
 * Pourquoi un service ? Quand on éteint la TV avec la télécommande, elle passe en VEILLE :
 * Android ne redémarre pas, donc l'événement "démarrage terminé" n'arrive pas au rallumage.
 * En revanche, Android diffuse ACTION_SCREEN_ON quand l'écran se rallume… mais cet événement
 * ne peut être écouté QUE par un code déjà en train de tourner. D'où ce service.
 */
class WatchService : Service() {

    // Handler = permet de programmer une action "dans X millisecondes" sur le fil principal
    private val handler = Handler(Looper.getMainLooper())

    // Heure (en ms depuis le démarrage) du dernier lancement de Plex, pour éviter les doublons
    private var lastLaunchAt = 0L

    /** L'action programmée : lancer Plex si l'automatisme est toujours activé. */
    private val launchRunnable = Runnable {
        val now = SystemClock.elapsedRealtime()
        // Anti-rebond : si l'écran clignote (on/off/on), on ne lance Plex qu'une fois en 5 s
        if (Prefs.isEnabled(this) && now - lastLaunchAt > 5_000) {
            lastLaunchAt = now
            PlexLauncher.launchTarget(this) // Plex ou dashboard, selon le réglage
        } else {
            EventLog.add(this, "Lancement ignoré (désactivé ou doublon)")
        }
    }

    /** v1.4.3 : moment de la dernière ouverture automatique (démarrage ou rallumage). */
    private var autoLaunchAt = 0L

    /**
     * v1.4.3 : « rattrapage ». Quelques secondes après l'ouverture automatique, on vérifie
     * que notre écran (rappel ou dashboard) est toujours visible. S'il a été recouvert
     * (le menu Google TV finit de se charger après nous lors d'un démarrage complet)
     * et que personne ne l'a quitté volontairement depuis, on le remet devant.
     */
    private val retryRunnable = Runnable {
        val coveredByAnotherApp = !ScreenState.ownScreenShowing
        val userAnsweredSince = ScreenState.userLeftAt >= autoLaunchAt
        if (Prefs.isEnabled(this) && PlexLauncher.ownScreenWanted(this) &&
            coveredByAnotherApp && !userAnsweredSince
        ) {
            EventLog.add(this, "Écran recouvert par une autre appli → remis devant")
            PlexLauncher.launchTarget(this)
        }
    }

    /** Programme les vérifications de rattrapage (en annulant les précédentes). */
    private fun scheduleRetries(vararg delaysMs: Long) {
        autoLaunchAt = SystemClock.elapsedRealtime()
        handler.removeCallbacks(retryRunnable)
        // On peut poster plusieurs fois le même Runnable : il s'exécutera à chaque délai
        for (d in delaysMs) handler.postDelayed(retryRunnable, d)
    }

    /** Récepteur qui écoute le rallumage de l'écran (sortie de veille). */
    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    EventLog.add(context, "Écran rallumé → ouverture dans ${Prefs.DELAY_AFTER_WAKE_MS / 1000} s")
                    scheduleLaunch(Prefs.DELAY_AFTER_WAKE_MS)
                    scheduleRetries(8_000)
                }
                // v1.4.1 : noté seulement pour le diagnostic (vérifier que la TV prévient bien)
                Intent.ACTION_SCREEN_OFF -> {
                    EventLog.add(context, "Écran éteint (veille)")
                    // TV éteinte : plus besoin d'ouvrir ni de rattraper quoi que ce soit
                    handler.removeCallbacks(launchRunnable)
                    handler.removeCallbacks(retryRunnable)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // 1) Un service de premier plan DOIT afficher une notification (règle Android).
        //    Sur Google TV, elle n'apparaît quasiment nulle part, elle ne gênera pas.
        createChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ : il faut préciser le type de service déclaré dans le manifeste
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        // 2) On s'abonne à l'événement "écran rallumé".
        //    RECEIVER_NOT_EXPORTED (Android 13+) : seules les diffusions du système nous parviennent.
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_SCREEN_OFF) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenOnReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenOnReceiver, filter)
        }
        EventLog.add(this, "Surveillance démarrée")
    }

    /**
     * Appelé à chaque démarrage du service (y compris s'il tournait déjà).
     * Si on nous demande de lancer Plex tout de suite (cas du démarrage de la TV), on le programme.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra(EXTRA_LAUNCH_AFTER_BOOT, false) == true) {
            EventLog.add(this, "Démarrage TV → ouverture dans ${Prefs.DELAY_AFTER_BOOT_MS / 1000} s")
            scheduleLaunch(Prefs.DELAY_AFTER_BOOT_MS)
            scheduleRetries(20_000, 40_000)
        }
        // START_STICKY : si Android tue le service par manque de mémoire, il le relance seul.
        return START_STICKY
    }

    /** Programme le lancement de Plex (en annulant un éventuel lancement déjà programmé). */
    private fun scheduleLaunch(delayMs: Long) {
        handler.removeCallbacks(launchRunnable)
        handler.postDelayed(launchRunnable, delayMs)
    }

    override fun onDestroy() {
        // On se désabonne proprement, sinon Android signale une "fuite" de récepteur.
        EventLog.add(this, "Surveillance arrêtée")
        handler.removeCallbacks(launchRunnable)
        handler.removeCallbacks(retryRunnable)
        unregisterReceiver(screenOnReceiver)
        super.onDestroy()
    }

    // Service "démarré" uniquement : personne ne s'y connecte, donc pas de Binder.
    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        // Les canaux de notification existent depuis Android 8 (notre minSdk), donc toujours ici.
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_MIN // le plus discret possible
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play) // icône système, pas besoin d'en dessiner une
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_text))
            .setOngoing(true)
            .build()

    companion object {
        private const val TAG = "PlexAuto"
        private const val CHANNEL_ID = "watch"
        private const val NOTIF_ID = 1
        private const val EXTRA_LAUNCH_AFTER_BOOT = "launch_after_boot"

        /**
         * Démarre le service (ou le "relance" s'il tourne déjà, sans le dupliquer).
         * @param launchAfterBoot true pour lancer Plex dans la foulée (démarrage de la TV)
         */
        fun start(context: Context, launchAfterBoot: Boolean) {
            val intent = Intent(context, WatchService::class.java)
                .putExtra(EXTRA_LAUNCH_AFTER_BOOT, launchAfterBoot)
            context.startForegroundService(intent)
        }

        /** Arrête le service (quand on désactive l'automatisme). */
        fun stop(context: Context) {
            context.stopService(Intent(context, WatchService::class.java))
        }
    }
}
