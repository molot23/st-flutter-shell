package com.molot23.stshell

import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.HttpAuthHandler
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

/**
 * MethodChannel helpers for the SillyTavern WebView shell:
 * - Trust self-signed / mismatched certs for ALL resources (not just the top frame)
 * - Answer HTTP Basic Auth challenges for CSS/JS/subresources
 * - Harden WebSettings (DOM storage, mixed content, etc.)
 */
class MainActivity : FlutterActivity() {
    private val channelName = "com.molot23.stshell/ssl"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "setBasicAuth" -> {
                        val username = call.argument<String>("username") ?: ""
                        val password = call.argument<String>("password") ?: ""
                        SslTrustHelper.setCredentials(username, password)
                        result.success(true)
                    }
                    "enableTrustSelfSigned" -> {
                        window.decorView.post {
                            findWebViews(window.decorView).forEach { webView ->
                                SslTrustHelper.install(webView)
                            }
                        }
                        // Retry shortly in case WebView is attached a frame later.
                        window.decorView.postDelayed({
                            findWebViews(window.decorView).forEach { webView ->
                                SslTrustHelper.install(webView)
                            }
                        }, 300)
                        window.decorView.postDelayed({
                            findWebViews(window.decorView).forEach { webView ->
                                SslTrustHelper.install(webView)
                            }
                        }, 1000)
                        result.success(true)
                    }
                    else -> result.notImplemented()
                }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    private fun findWebViews(root: View): List<WebView> {
        val out = mutableListOf<WebView>()
        if (root is WebView) {
            out.add(root)
        } else if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                out.addAll(findWebViews(root.getChildAt(i)))
            }
        }
        return out
    }
}

/**
 * Wraps the existing [WebViewClient] (Flutter's) and:
 * - [onReceivedSslError] → proceed (self-signed FRP tunnels)
 * - [onReceivedHttpAuthRequest] → proceed with stored Basic Auth
 * - Applies WebSettings needed for SillyTavern (DOM storage, mixed content)
 *
 * Re-install is idempotent per WebView instance, but credentials can change.
 */
object SslTrustHelper {
    @Volatile private var basicUser: String = ""
    @Volatile private var basicPass: String = ""

    private val installed = mutableSetOf<Int>()

    fun setCredentials(username: String, password: String) {
        basicUser = username
        basicPass = password
    }

    fun install(webView: WebView) {
        applySettings(webView)

        val id = System.identityHashCode(webView)
        if (installed.contains(id)) {
            // Refresh saved HTTP auth for this host if credentials exist.
            seedHttpAuth(webView)
            return
        }

        val existing: WebViewClient = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.webViewClient
        } else {
            WebViewClient()
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?,
            ) {
                // Trust self-signed / mismatched certs used by FRP tunnels.
                // Applies to main document AND subresources (CSS/JS/fonts).
                handler?.proceed()
            }

            override fun onReceivedHttpAuthRequest(
                view: WebView?,
                handler: HttpAuthHandler?,
                host: String?,
                realm: String?,
            ) {
                if (basicUser.isNotEmpty() || basicPass.isNotEmpty()) {
                    handler?.proceed(basicUser, basicPass)
                } else {
                    // Fall through to Flutter's client if it has a handler.
                    existing.onReceivedHttpAuthRequest(view, handler, host, realm)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                @Suppress("DEPRECATION")
                return existing.shouldOverrideUrlLoading(view, url)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    existing.shouldOverrideUrlLoading(view, request)
                } else {
                    false
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                existing.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                existing.onPageFinished(view, url)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?,
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    existing.onReceivedError(view, request, error)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?,
            ) {
                @Suppress("DEPRECATION")
                existing.onReceivedError(view, errorCode, description, failingUrl)
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?,
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    existing.onReceivedHttpError(view, request, errorResponse)
                }
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                existing.doUpdateVisitedHistory(view, url, isReload)
            }

            override fun onLoadResource(view: WebView?, url: String?) {
                existing.onLoadResource(view, url)
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
            ): WebResourceResponse? {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    existing.shouldInterceptRequest(view, request)
                } else {
                    null
                }
            }
        }

        seedHttpAuth(webView)
        installed.add(id)
    }

    private fun applySettings(webView: WebView) {
        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.javaScriptCanOpenWindowsAutomatically = true
        s.mediaPlaybackRequiresUserGesture = false
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.allowFileAccess = true
        s.allowContentAccess = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        // Keep cache so ST assets / service-worker-ish loads work better.
        s.cacheMode = WebSettings.LOAD_DEFAULT
    }

    private fun seedHttpAuth(webView: WebView) {
        if (basicUser.isEmpty() && basicPass.isEmpty()) return
        val url = webView.url ?: return
        try {
            val uri = android.net.Uri.parse(url)
            val host = uri.host ?: return
            // Empty realm is accepted by WebView as a wildcard for many servers.
            webView.setHttpAuthUsernamePassword(host, "", basicUser, basicPass)
                    } catch (_: Throwable) {
            // Ignore malformed URLs / API quirks.
        }
    }
}
