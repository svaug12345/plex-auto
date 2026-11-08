package fr.baiji.plexauto

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Écran « Les tâches du jour sont faites ? », affiché à la place de la cible
 * quand la TV s'allume pendant le créneau du rappel (17h00 – 19h30 par défaut).
 *
 * v1.3.0 : l'écran est une page HTML stylisée (assets/rappel.html) affichée dans une WebView.
 * Le partage du travail :
 *   - la page HTML s'occupe de l'AFFICHAGE (style, cartes par enfant, boutons) ;
 *   - ce code Kotlin TÉLÉCHARGE les tâches du Google Sheet et ouvre Plex / le dashboard.
 *
 * Pourquoi télécharger côté Kotlin plutôt qu'en JavaScript ? Une page chargée depuis l'APK
 * n'a pas d'"adresse web" à elle, et certains serveurs refusent alors les requêtes (règles CORS).
 * En Kotlin, ces règles n'existent pas : c'est plus fiable.
 */
class TaskCheckActivity : Activity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#050f12")) // même fond que la page : pas de flash blanc
            settings.javaScriptEnabled = true

            // "Pont" entre JavaScript et Kotlin : dans la page, Android.openPlex() appellera
            // la méthode openPlex() de la classe Bridge ci-dessous.
            addJavascriptInterface(Bridge(), "Android")

            webViewClient = object : WebViewClient() {
                // La page est prête : on peut lancer le téléchargement des tâches
                override fun onPageFinished(view: WebView, url: String) = fetchTasks()
            }
        }
        setContentView(webView)

        // file:///android_asset/ = le dossier "assets" de l'APK
        webView.loadUrl("file:///android_asset/rappel.html")
    }

    /** Si l'écran est déjà affiché et que la TV se rallume, on actualise les tâches. */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        webView.reload()
    }

    /**
     * Télécharge le Google Sheet "Family tasks" (format TSV) dans un fil d'exécution séparé
     * (Android interdit les accès réseau sur le fil principal, qui gère l'affichage),
     * puis transmet le texte à la fonction JavaScript showTasks().
     */
    private fun fetchTasks() {
        Thread {
            val tsv: String? = try {
                val conn = URL(Prefs.TASKS_TSV_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.useCaches = false
                // Google répond parfois par une redirection : HttpURLConnection la suit tout seul
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } catch (e: Exception) {
                Log.w("PlexAuto", "Tâches indisponibles", e)
                null
            }
            // JSONObject.quote() transforme le texte en chaîne JavaScript valide
            // (guillemets, tabulations et retours à la ligne correctement échappés).
            val arg = if (tsv == null) "null" else JSONObject.quote(tsv)
            // Retour sur le fil principal : seul lui a le droit de toucher à la WebView
            runOnUiThread {
                if (!isDestroyed) webView.evaluateJavascript("showTasks($arg)", null)
            }
        }.start()
    }

    /**
     * Méthodes appelables depuis la page HTML. @JavascriptInterface est obligatoire :
     * sans cette annotation, Android cache la méthode à JavaScript (sécurité).
     * Elles s'exécutent sur un fil "JavaScript" : on repasse sur le fil principal avec runOnUiThread.
     */
    inner class Bridge {
        @JavascriptInterface
        fun openPlex() = runOnUiThread {
            PlexLauncher.launchPlex(this@TaskCheckActivity)
            finish() // on ferme le rappel pour qu'il ne reste pas "derrière" Plex
        }

        @JavascriptInterface
        fun openDashboard() = runOnUiThread {
            PlexLauncher.launchDashboard(this@TaskCheckActivity)
            finish()
        }
    }

    /**
     * La touche Retour est neutralisée : la question attend une vraie réponse.
     * En renvoyant true sans "suivre" l'appui, Android ne déclenche pas le retour.
     * (Le bouton Accueil, lui, ramène toujours au menu normal de la TV.)
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        webView.destroy() // libère la mémoire de la WebView
        super.onDestroy()
    }
}
