package fr.baiji.plexauto

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Écran « Les tâches du jour sont faites ? », affiché à l'allumage de la TV.
 *
 * Le partage du travail :
 *   - la page HTML (rappel.html) s'occupe de l'AFFICHAGE (style, cartes par enfant, boutons) ;
 *   - ce code Kotlin TÉLÉCHARGE les tâches du Google Sheet et ouvre Plex / le dashboard / l'accueil TV.
 *
 * v1.4.0 : la page est chargée depuis baiji.fr (Prefs.REMINDER_URL), pour pouvoir changer
 * le texte ou le style sans mettre à jour l'appli. Si le site ne répond pas (Wi-Fi pas prêt…),
 * on affiche la copie embarquée dans l'APK (assets/rappel.html).
 *
 * Pourquoi télécharger les tâches côté Kotlin plutôt qu'en JavaScript ? Pour la copie embarquée,
 * la page n'a pas d'"adresse web" à elle et certains serveurs refusent alors les requêtes (CORS).
 * En Kotlin, ces règles n'existent pas : ça marche dans les deux cas.
 */
class TaskCheckActivity : Activity() {

    private lateinit var webView: WebView

    /** Adresse de la copie de secours, dans le dossier "assets" de l'APK. */
    private val localCopy = "file:///android_asset/rappel.html"

    /** Passe à true dès qu'on bascule sur la copie locale, pour ne pas boucler en cas d'erreur. */
    private var usingLocalCopy = false

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#050f12")) // même fond que la page : pas de flash blanc
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // Toujours la dernière version de la page du site (pas de copie périmée en cache)
            settings.cacheMode = WebSettings.LOAD_NO_CACHE

            // "Pont" entre JavaScript et Kotlin : dans la page, Android.openPlex() appellera
            // la méthode openPlex() de la classe Bridge ci-dessous.
            addJavascriptInterface(Bridge(), "Android")

            webViewClient = object : WebViewClient() {
                // La page est prête : on peut lancer le téléchargement des tâches
                override fun onPageFinished(view: WebView, url: String) = fetchTasks()

                // On reste sur baiji.fr : un lien vers un autre site est bloqué (true = "j'intercepte,
                // ne charge pas"). Les redirections du serveur, elles, sont autorisées.
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val host = request.url.host ?: return true
                    return !(request.isRedirect || host == "baiji.fr" || host.endsWith(".baiji.fr"))
                }

                // Pas de réseau, DNS en échec… → copie locale
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) fallBackToLocalCopy("erreur réseau : ${error.description}")
                }

                // Le site répond, mais avec une erreur (404, 500…) → copie locale aussi
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (request.isForMainFrame) fallBackToLocalCopy("erreur HTTP ${response.statusCode}")
                }
            }
        }
        setContentView(webView)
        webView.loadUrl(Prefs.REMINDER_URL)
    }

    /** Bascule une seule fois sur la copie embarquée dans l'APK. */
    private fun fallBackToLocalCopy(reason: String) {
        if (usingLocalCopy) return
        usingLocalCopy = true
        Log.w("PlexAuto", "Rappel du site indisponible ($reason) → copie locale")
        webView.loadUrl(localCopy)
    }

    /** Si l'écran est déjà affiché et que la TV se rallume, on retente le site et on actualise. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        usingLocalCopy = false
        webView.loadUrl(Prefs.REMINDER_URL)
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

        /** v1.4.0 : bouton discret « Menu TV » → écran d'accueil habituel de Google TV. */
        @JavascriptInterface
        fun openHome() = runOnUiThread {
            PlexLauncher.launchHome(this@TaskCheckActivity)
            finish()
        }
    }

    /**
     * La touche Retour est neutralisée : la question attend une vraie réponse.
     * En renvoyant true sans "suivre" l'appui, Android ne déclenche pas le retour.
     * (Le bouton Accueil de la télécommande, lui, ramène toujours au menu de la TV.)
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
