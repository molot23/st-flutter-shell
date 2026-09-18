import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:webview_flutter_android/webview_flutter_android.dart';

import '../services/settings_service.dart';

/// Modern Chrome-on-Android UA so SillyTavern serves the full web UI.
const String _kMobileChromeUa =
    'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 '
    '(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36';

class WebViewScreen extends StatefulWidget {
  const WebViewScreen({super.key, required this.initialUrl});

  final String initialUrl;

  @override
  State<WebViewScreen> createState() => _WebViewScreenState();
}

class _WebViewScreenState extends State<WebViewScreen> {
  static const MethodChannel _sslChannel =
      MethodChannel('com.molot23.stshell/ssl');

  WebViewController? _controller;
  var _loading = true;
  var _progress = 0;
  String? _title;
  String _basicUser = '';
  String _basicPass = '';

  @override
  void initState() {
    super.initState();
    _initWebView();
  }

  Future<void> _initWebView() async {
    final user = await SettingsService.instance.getUsername();
    final pass = await SettingsService.instance.getPassword();
    _basicUser = user;
    _basicPass = pass;

    final headers = <String, String>{};
    if (user.isNotEmpty || pass.isNotEmpty) {
      final token = base64Encode(utf8.encode('$user:$pass'));
      headers['Authorization'] = 'Basic $token';
    }

    // Push credentials to native side so WebViewClient can answer HTTP auth
    // challenges for CSS/JS/subresources (loadRequest headers only cover the
    // main document).
    try {
      await _sslChannel.invokeMethod('setBasicAuth', {
        'username': user,
        'password': pass,
      });
    } catch (e) {
      debugPrint('setBasicAuth channel: $e');
    }

    late final WebViewController controller;
    controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      // Neutral background: do not force a dark canvas that hides unstyled ST text.
      ..setBackgroundColor(const Color(0xFFFFFFFF))
      ..setNavigationDelegate(
        NavigationDelegate(
          onProgress: (p) {
            if (mounted) setState(() => _progress = p);
          },
          onPageStarted: (_) {
            if (mounted) setState(() => _loading = true);
            _installNativeWebViewHooks();
          },
          onPageFinished: (_) async {
            final t = await controller.getTitle();
            if (mounted) {
              setState(() {
                _loading = false;
                _title = t;
              });
            }
          },
          onWebResourceError: (err) {
            debugPrint(
              'WebView error: ${err.errorCode} ${err.description} '
              '(mainFrame=${err.isForMainFrame})',
            );
          },
          onHttpError: (err) {
            debugPrint(
              'WebView HTTP ${err.response?.statusCode} '
              'url=${err.request?.uri}',
            );
          },
          // Critical: default cancels → CSS/JS behind Basic Auth never load.
          onHttpAuthRequest: (request) {
            if (_basicUser.isNotEmpty || _basicPass.isNotEmpty) {
              request.onProceed(
                WebViewCredential(user: _basicUser, password: _basicPass),
              );
            } else {
              request.onCancel();
            }
          },
          // Critical: default cancels self-signed TLS for every resource.
          onSslAuthError: (error) async {
            await error.proceed();
          },
        ),
      );

    if (controller.platform is AndroidWebViewController) {
      final android = controller.platform as AndroidWebViewController;
      await android.setMediaPlaybackRequiresUserGesture(false);
      await android.setUserAgent(_kMobileChromeUa);
      await android.enableZoom(true);
      await android.setMixedContentMode(MixedContentMode.alwaysAllow);
      await android.setAllowFileAccess(true);
      await android.setAllowContentAccess(true);
      // DOM storage is already enabled by AndroidWebViewController defaults.

      final cookieManager = WebViewCookieManager();
      final platformCookies = cookieManager.platform;
      if (platformCookies is AndroidWebViewCookieManager) {
        await platformCookies.setAcceptThirdPartyCookies(android, true);
      }

      if (kDebugMode) {
        await AndroidWebViewController.enableDebugging(true);
      }
    }

    if (mounted) {
      setState(() => _controller = controller);
    }

    // Install native SSL / auth / settings after the platform view exists.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _installNativeWebViewHooks();
    });

    await controller.loadRequest(
      Uri.parse(widget.initialUrl),
      headers: headers,
    );
  }

  void _installNativeWebViewHooks() {
    _sslChannel.invokeMethod('enableTrustSelfSigned').catchError((Object e) {
      debugPrint('SSL channel: $e');
      return null;
    });
  }

  Future<bool> _handleBack() async {
    final c = _controller;
    if (c != null && await c.canGoBack()) {
      await c.goBack();
      return false;
    }
    return true;
  }

  Future<void> _reload() async {
    final c = _controller;
    if (c == null) return;
    // Re-apply auth header on explicit reload of the top-level document.
    final headers = <String, String>{};
    if (_basicUser.isNotEmpty || _basicPass.isNotEmpty) {
      final token = base64Encode(utf8.encode('$_basicUser:$_basicPass'));
      headers['Authorization'] = 'Basic $token';
    }
    _installNativeWebViewHooks();
    final current = await c.currentUrl();
    final url = (current != null && current.isNotEmpty)
        ? current
        : widget.initialUrl;
    await c.loadRequest(Uri.parse(url), headers: headers);
  }

  @override
  Widget build(BuildContext context) {
    final controller = _controller;
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) async {
        if (didPop) return;
        final shouldPop = await _handleBack();
        if (shouldPop && context.mounted) {
          Navigator.of(context).pop();
        }
      },
      child: Scaffold(
        appBar: AppBar(
          backgroundColor: const Color(0xFF1E1E1E),
          title: Text(
            _title?.isNotEmpty == true ? _title! : '家庭酒馆',
            overflow: TextOverflow.ellipsis,
          ),
          actions: [
            IconButton(
              tooltip: 'Reload',
              icon: const Icon(Icons.refresh),
              onPressed: controller == null ? null : _reload,
            ),
          ],
          bottom: PreferredSize(
            preferredSize: const Size.fromHeight(2),
            child: _loading
                ? LinearProgressIndicator(
                    value: _progress > 0 && _progress < 100
                        ? _progress / 100.0
                        : null,
                    minHeight: 2,
                    color: const Color(0xFF7C4DFF),
                    backgroundColor: Colors.transparent,
                  )
                : const SizedBox(height: 2),
          ),
        ),
        body: controller == null
            ? const Center(child: CircularProgressIndicator())
            : WebViewWidget(controller: controller),
      ),
    );
  }
}
