package fr.baiji.plexauto

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Écran « Est-ce que les tâches sont faites ? », affiché à la place de la cible
 * quand la TV s'allume pendant le créneau du rappel (17h00 – 19h30 par défaut).
 *
 * Deux boutons, navigables à la télécommande :
 *  - « Plex »      → ouvre Plex
 *  - « Dashboard » → ouvre le dashboard famille (pour vérifier ses tâches)
 *
 * La touche Retour est neutralisée : il faut choisir l'un des deux.
 */
class TaskCheckActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Conteneur vertical centré, fond sombre
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#101418"))
            setPadding(dp(64), dp(48), dp(64), dp(48))
        }

        // Le gros message, lisible depuis le canapé
        val question = TextView(this).apply {
            text = getString(R.string.task_question)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 52f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        // Petite ligne d'explication sous la question
        val hint = TextView(this).apply {
            text = getString(R.string.task_hint)
            setTextColor(Color.parseColor("#C8D0D8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(40))
        }

        // Rangée horizontale avec les deux boutons
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val plexButton = bigButton(getString(R.string.task_btn_plex)) {
            PlexLauncher.launchPlex(this)
            finish() // on ferme la question pour qu'elle ne reste pas "derrière" Plex
        }
        val dashboardButton = bigButton(getString(R.string.task_btn_dashboard)) {
            PlexLauncher.launchDashboard(this)
            finish()
        }

        // Marges entre les deux boutons
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(dp(24), 0, dp(24), 0) }
        row.addView(dashboardButton, lp)
        row.addView(plexButton, lp)

        root.addView(question)
        root.addView(hint)
        root.addView(row)
        setContentView(root)

        // Focus initial sur « Dashboard » : on pousse gentiment à aller vérifier ses tâches 😉
        dashboardButton.requestFocus()
    }

    /** Fabrique un gros bouton lisible à 3 m, avec l'action à exécuter au clic. */
    private fun bigButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        setPadding(dp(32), dp(16), dp(32), dp(16))
        setOnClickListener { onClick() }
    }

    /** On ignore la touche Retour : la question attend une vraie réponse. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) return true
        return super.onKeyDown(keyCode, event)
    }

    /** Convertit des "dp" en pixels réels. */
    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
}
