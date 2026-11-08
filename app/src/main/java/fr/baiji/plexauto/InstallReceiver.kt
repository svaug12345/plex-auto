package fr.baiji.plexauto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import android.widget.Toast

/**
 * v1.4.0 : reçoit la réponse de l'installateur d'Android après Updater.downloadAndInstall().
 *
 * Trois cas :
 *  - PENDING_USER_ACTION : Android veut une confirmation → on affiche son écran « Mettre à jour ? »
 *  - SUCCESS             : c'est installé (l'appli redémarre ; BootReceiver relance le service
 *                          grâce à l'événement MY_PACKAGE_REPLACED)
 *  - autre               : échec (signature différente, place insuffisante…) → message à l'écran
 */
class InstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // L'écran de confirmation d'Android est fourni dans l'intent, sous EXTRA_INTENT
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { context.startActivity(it) }
            }
            PackageInstaller.STATUS_SUCCESS -> Log.i("PlexAuto", "Mise à jour installée")
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "erreur $status"
                Log.e("PlexAuto", "Échec de la mise à jour : $msg")
                Toast.makeText(context, "Échec de la mise à jour : $msg", Toast.LENGTH_LONG).show()
            }
        }
    }
}
