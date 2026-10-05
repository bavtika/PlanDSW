package app.plandsw.data

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/** Cookies of the sign-in WebView: the USOSweb session obtained after login is shared with OkHttp requests, and back. */
class WebViewCookieJar : CookieJar {
    private val manager get() = CookieManager.getInstance()

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        manager.getCookie(url.toString())
            ?.split(';')
            ?.mapNotNull { Cookie.parse(url, it.trim()) }
            .orEmpty()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { manager.setCookie(url.toString(), it.toString()) }
        manager.flush()
    }
}
