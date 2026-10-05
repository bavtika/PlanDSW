package app.plandsw.data

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Renews the USOSweb session without user interaction: a hidden WebView follows [UsosApi.SILENT_LOGIN_URL]
 * (CAS → Microsoft) and, while the Microsoft cookies are still valid, returns to USOSweb within a few seconds.
 * If Microsoft asks for a password or MFA, the WebView just stays on the sign-in page — we give up on timeout.
 */
class UsosSilentLogin(private val context: Context) {
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun run(): Boolean = withContext(Dispatchers.Main) {
        val web = WebView(context)
        try {
            web.settings.javaScriptEnabled = true
            web.settings.domStorageEnabled = true
            CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
            val ok = withTimeoutOrNull(TIMEOUT_MS) {
                suspendCancellableCoroutine<Boolean> { cont ->
                    web.webViewClient = object : WebViewClient() {
                        // intent://, msauth:// — never open other apps in silent mode.
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                            request.url.scheme != "http" && request.url.scheme != "https"

                        override fun onPageFinished(view: WebView, url: String) {
                            if (UsosApi.isAfterLogin(url) && cont.isActive) {
                                CookieManager.getInstance().flush()
                                cont.resume(true)
                            }
                        }
                    }
                    web.loadUrl(UsosApi.SILENT_LOGIN_URL)
                }
            } ?: false
            Log.i(TAG, if (ok) "renewed" else "needs user")
            ok
        } finally {
            web.stopLoading()
            web.destroy()
        }
    }

    companion object {
        private const val TAG = "UsosSilentLogin"
        private const val TIMEOUT_MS = 25_000L
    }
}
