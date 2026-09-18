package com.molot23.stshell

import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

/**
 * Hosts a MethodChannel that wraps every WebView's WebViewClient so
 * [WebViewClient.onReceivedSslError] calls [SslErrorHandler.proceed].
 * Needed for self-signed HTTPS endpoints you control.
 */
class MainActivity : FlutterActivity() {
    private val channelName = "com.molot23.stshell/ssl"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName)
            .setMethodCallHandler { call, result ->
                when (call.method) {
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
 * Wraps the existing [WebViewClient] (Flutter's) and only overrides SSL error
 * handling so navigation / progress callbacks keep working.
 */
object SslTrustHelper {
    private val installed = mutableSetOf<Int>()

    fun install(webView: WebView) {
        val id = System.identityHashCode(webView)
        if (installed.contains(id)) return

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
                handler?.proceed()
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                @Suppress("DEPRECATION")
                return existing.shouldOverrideUrlLoading(view, url)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
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
                request: android.webkit.WebResourceRequest?,
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
                request: android.webkit.WebResourceRequest?,
                errorResponse: android.webkit.WebResourceResponse?,
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
                request: android.webkit.WebResourceRequest?,
            ): android.webkit.WebResourceResponse? {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    existing.shouldInterceptRequest(view, request)
                } else {
                    null
                }
            }
        }

        installed.add(id)
    }
}
