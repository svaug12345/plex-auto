package fr.baiji.plexauto

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Écran plein écran qui affiche le dashboard famille dans un navigateur intégré (WebView).
 *
 * Pourquoi pas simplement ouvrir Chrome ? Sur Google TV, il n'y a pas de vrai navigateur
 * installé par défaut, et même avec un navigateur on garderait la barre d'adresse.
 * Ici on a un affichage "kiosque" : la page, et rien d'autre.
 */
class DashboardActivity : Activity() {

    private lateinit var webView: WebView

    // SetJavaScriptEnabled : Android Studio avertit que le JavaScript peut être risqué ;
    // c'est sans danger ici puisqu'on n'affiche que TA page.
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK) // fond noir pendant le chargement (pixels éteints sur OLED)

            settings.javaScriptEnabled = true   // le dashboard est une appli JS
            settings.domStorageEnabled = true   // autorise localStorage (réglages, cache de la page)
            settings.mediaPlaybackRequiresUserGesture = false // si la page joue un son / une vidéo

            // On garde la navigation DANS la WebView (sinon un lien ouvrirait une autre appli).
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false

                // Si le Wi-Fi n'est pas encore prêt au démarrage, la page échoue :
                // on réessaie automatiquement 10 s plus tard.
                override fun onReceivedError(
                    view: WebView, request: WebResourceRequest, error: WebResourceError
                ) {
                    if (request.isForMainFrame) {
                        view.postDelayed({ view.loadUrl(Prefs.dashboardUrl(this@DashboardActivity)) }, 10_000)
                    }
                }
            }
        }
        setContentView(webView)

        // Pas de restauration d'état : on recharge toujours la page à jour.
        webView.loadUrl(Prefs.dashboardUrl(this))
    }

    /** Appelé quand le dashboard est déjà ouvert et qu'on le relance (sortie de veille). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        webView.loadUrl(Prefs.dashboardUrl(this)) // on recharge pour avoir des données fraîches
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** Cache les barres système pour un vrai plein écran (rarement visibles sur TV, mais par sécurité). */
    private fun hideSystemBars() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    /**
     * Touches de la télécommande :
     *  - Retour : revient en arrière dans la page si possible, sinon ferme le dashboard.
     *  - OK long / Menu : recharge la page (pratique après une modif du dashboard).
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            webView.reload()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        // On libère proprement la WebView (elle consomme pas mal de mémoire)
        webView.destroy()
        super.onDestroy()
    }
}
