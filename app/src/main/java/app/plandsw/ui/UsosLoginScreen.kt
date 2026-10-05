package app.plandsw.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import app.plandsw.R
import app.plandsw.data.UsosApi

/**
 * USOSweb login on the university site: CAS → Microsoft (with MFA). The password is entered in the WebView,
 * the app never sees it; after returning to USOSweb the session cookies stay in CookieManager.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsosLoginScreen(onLoggedIn: () -> Unit, onBack: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var done by remember { mutableStateOf(false) }
    val finish by rememberUpdatedState(onLoggedIn)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.usos_login_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val uri = request.url
                                if (uri.scheme == "http" || uri.scheme == "https") return false
                                // intent://, msauth:// - Microsoft may call the Authenticator app.
                                runCatching {
                                    val intent = if (uri.scheme == "intent") Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                                    else Intent(Intent.ACTION_VIEW, uri)
                                    // The page may only open what a browser would: no explicit components.
                                    intent.addCategory(Intent.CATEGORY_BROWSABLE)
                                    intent.component = null
                                    intent.selector = null
                                    view.context.startActivity(intent)
                                }
                                return true
                            }

                            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                loading = true
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                loading = false
                                if (!done && UsosApi.isAfterLogin(url)) {
                                    done = true
                                    CookieManager.getInstance().flush()
                                    finish()
                                }
                            }
                        }
                        loadUrl(UsosApi.LOGIN_URL)
                    }
                },
                onRelease = { it.destroy() },
            )
            if (loading) LoadingBar(Modifier.fillMaxWidth())
        }
    }
}
