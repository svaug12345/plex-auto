package fr.baiji.plexauto

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Écran de réglages, ouvert depuis la liste des applis de la TV.
 *
 * L'interface est construite en code plutôt qu'en XML : pour quelques textes et boutons,
 * c'est plus simple à lire d'un seul coup d'œil, et ça évite toute dépendance.
 * Tout est navigable à la croix directionnelle de la télécommande.
 */
class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    private lateinit var targetButton: Button
    private lateinit var urlField: EditText
    private lateinit var reminderButton: Button
    private lateinit var updateButton: Button

    /** v1.4.0 : dernière version trouvée sur GitHub (null = pas encore vérifié ou déjà à jour). */
    private var pendingRelease: Updater.Release? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Conteneur vertical centré, fond sombre (confortable sur une TV OLED)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#101418"))
            setPadding(dp(48), dp(24), dp(48), dp(24))
        }

        val title = TextView(this).apply {
            text = getString(R.string.app_name) + "  v" + versionName()
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
        }

        statusText = TextView(this).apply {
            setTextColor(Color.parseColor("#C8D0D8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setPadding(0, dp(16), 0, dp(16))
        }

        // Bouton Activer / Désactiver l'automatisme
        toggleButton = Button(this).apply {
            setOnClickListener {
                val enable = !Prefs.isEnabled(this@MainActivity)
                Prefs.setEnabled(this@MainActivity, enable)
                if (enable) WatchService.start(this@MainActivity, launchAfterBoot = false)
                else WatchService.stop(this@MainActivity)
                refresh()
            }
        }

        // Bouton qui bascule la cible : Plex ⇄ Dashboard
        targetButton = Button(this).apply {
            setOnClickListener {
                val next = if (Prefs.target(this@MainActivity) == Prefs.Target.PLEX)
                    Prefs.Target.DASHBOARD else Prefs.Target.PLEX
                Prefs.setTarget(this@MainActivity, next)
                refresh()
            }
        }

        // Champ de saisie de l'adresse du dashboard (le clavier de la TV s'ouvre en cliquant dessus)
        urlField = EditText(this).apply {
            setText(Prefs.dashboardUrl(this@MainActivity))
            setTextColor(Color.WHITE)
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_DONE
            // Quand on valide au clavier ("OK"/"Terminé"), on enregistre l'adresse
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) { saveUrl(); true } else false
            }
        }

        // Bouton qui active / désactive le rappel des tâches (17h – 19h30)
        reminderButton = Button(this).apply {
            setOnClickListener {
                Prefs.setReminderEnabled(this@MainActivity, !Prefs.isReminderEnabled(this@MainActivity))
                refresh()
            }
        }

        // v1.4.0 : bouton de mise à jour. 1er clic = vérifier ; s'il y a mieux, il devient
        // « Installer la version X » et le clic suivant télécharge et installe.
        updateButton = Button(this).apply {
            text = getString(R.string.btn_update_check)
            setOnClickListener {
                val release = pendingRelease
                if (release == null) checkForUpdate(showResult = true) else installUpdate(release)
            }
        }

        // Bouton de test : ouvre la cible tout de suite
        val testButton = Button(this).apply {
            text = getString(R.string.btn_test)
            setOnClickListener {
                saveUrl() // au cas où l'adresse vient d'être tapée sans valider
                if (!PlexLauncher.launchTarget(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, R.string.plex_missing, Toast.LENGTH_LONG).show()
                }
            }
        }

        root.addView(title)
        root.addView(statusText)
        root.addView(toggleButton)
        root.addView(targetButton)
        root.addView(urlField)
        root.addView(reminderButton)
        root.addView(testButton)
        root.addView(updateButton)
        setContentView(root)

        // Si l'automatisme est actif, on s'assure que le service tourne
        // (utile juste après la première installation, avant tout redémarrage).
        if (Prefs.isEnabled(this)) WatchService.start(this, launchAfterBoot = false)

        toggleButton.requestFocus() // focus initial pour la télécommande
    }

    // onResume est appelé à chaque retour sur l'écran : on rafraîchit l'état affiché
    override fun onResume() {
        super.onResume()
        refresh()
        // v1.4.0 : vérification discrète à l'ouverture (sans message si déjà à jour)
        if (pendingRelease == null) checkForUpdate(showResult = false)
    }

    /**
     * v1.4.0 : demande à GitHub s'il existe une version plus récente.
     * Le réseau se fait dans un Thread ; l'affichage revient sur le fil principal (runOnUiThread).
     * @param showResult true = afficher aussi « Déjà à jour » / « Impossible de vérifier »
     */
    private fun checkForUpdate(showResult: Boolean) {
        if (showResult) updateButton.text = getString(R.string.btn_update_checking)
        Thread {
            val latest = Updater.fetchLatest()
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                when {
                    latest == null -> {
                        updateButton.text = getString(R.string.btn_update_check)
                        if (showResult) toast("Impossible de vérifier (réseau ou GitHub indisponible)")
                    }
                    Updater.isNewer(latest.version, versionName()) -> {
                        pendingRelease = latest
                        updateButton.text = getString(R.string.btn_update_install, latest.version)
                    }
                    else -> {
                        updateButton.text = getString(R.string.btn_update_check)
                        if (showResult) toast("Déjà à jour (v${versionName()})")
                    }
                }
            }
        }.start()
    }

    /** v1.4.0 : télécharge et installe la version trouvée, avec le pourcentage sur le bouton. */
    private fun installUpdate(release: Updater.Release) {
        updateButton.isEnabled = false
        Thread {
            try {
                Updater.downloadAndInstall(this, release) { pct ->
                    runOnUiThread { updateButton.text = getString(R.string.btn_update_progress, pct) }
                }
                // La suite (écran de confirmation d'Android) est gérée par InstallReceiver
            } catch (e: Exception) {
                runOnUiThread {
                    updateButton.isEnabled = true
                    updateButton.text = getString(R.string.btn_update_install, release.version)
                    toast("Échec du téléchargement : ${e.message}")
                }
            }
        }.start()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    // En quittant l'écran, on enregistre l'adresse même si on n'a pas validé au clavier
    override fun onPause() {
        saveUrl()
        super.onPause()
    }

    /** Enregistre l'adresse tapée, en ajoutant https:// si on l'a oublié. */
    private fun saveUrl() {
        var url = urlField.text.toString().trim()
        if (url.isEmpty()) return
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        Prefs.setDashboardUrl(this, url)
        urlField.setText(url)
    }

    /** Met à jour les textes selon l'état réel (cible, Plex installé, permission accordée…). */
    private fun refresh() {
        val enabled = Prefs.isEnabled(this)
        val target = Prefs.target(this)
        val plexOk = PlexLauncher.isInstalled(this)
        // canDrawOverlays = la permission "afficher par-dessus" a-t-elle été accordée (via ADB) ?
        val overlayOk = Settings.canDrawOverlays(this)

        statusText.text = buildString {
            appendLine(if (enabled) "✅ Automatisme activé" else "⏸️ Automatisme désactivé")
            appendLine(
                if (target == Prefs.Target.PLEX) "🎬 Cible : Plex"
                else "🏠 Cible : dashboard famille (adresse ci-dessous)"
            )
            appendLine(
                if (Prefs.isReminderEnabled(this@MainActivity))
                    "❓ Rappel des tâches ${Prefs.reminderWindowLabel()} : activé" +
                        // "(en cours maintenant)" n'a de sens que si un créneau horaire est actif
                        (if (!Prefs.REMINDER_ALL_DAY && Prefs.isInReminderWindow()) " (en cours maintenant)" else "")
                else "❓ Rappel des tâches : désactivé"
            )
            // L'état de Plex n'a d'intérêt que si c'est la cible
            if (target == Prefs.Target.PLEX) {
                appendLine(if (plexOk) "✅ Plex est installé" else "❌ Plex n'est pas installé")
            }
            appendLine(
                if (overlayOk) "✅ Permission « par-dessus les autres applis » accordée"
                else "❌ Permission manquante : sans elle, Android bloque l'ouverture automatique.\n" +
                    "   Commande ADB : appops set $packageName SYSTEM_ALERT_WINDOW allow"
            )
            // v1.4.0 : l'appli a-t-elle le droit d'installer ses mises à jour ?
            appendLine(
                if (packageManager.canRequestPackageInstalls()) "✅ Mises à jour depuis la TV autorisées"
                else "⚠️ Mises à jour depuis la TV pas encore autorisées.\n" +
                    "   Commande ADB : appops set $packageName REQUEST_INSTALL_PACKAGES allow"
            )
        }
        toggleButton.text = getString(if (enabled) R.string.btn_disable else R.string.btn_enable)
        targetButton.text = getString(
            if (target == Prefs.Target.PLEX) R.string.btn_to_dashboard else R.string.btn_to_plex
        )
        reminderButton.text = getString(
            if (Prefs.isReminderEnabled(this)) R.string.btn_reminder_off else R.string.btn_reminder_on
        )
        // Le champ d'adresse ne sert que pour le dashboard
        urlField.isEnabled = target == Prefs.Target.DASHBOARD
        urlField.alpha = if (target == Prefs.Target.DASHBOARD) 1f else 0.4f
    }

    /** Convertit des "dp" (unités indépendantes de la densité) en pixels réels. */
    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    /** Lit le numéro de version déclaré dans build.gradle.kts, pour l'afficher dans le titre. */
    private fun versionName(): String =
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
}
