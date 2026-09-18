import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:webview_flutter_android/webview_flutter_android.dart';

import '../services/settings_service.dart';

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

  @override
  void initState() {
    super.initState();
    _initWebView();
  }

  Future<void> _initWebView() async {
    final user = await SettingsService.instance.getUsername();
    final pass = await SettingsService.instance.getPassword();
    final headers = <String, String>{};
    if (user.isNotEmpty || pass.isNotEmpty) {
      final token = base64Encode(utf8.encode('$user:$pass'));
      headers['Authorization'] = 'Basic $token';
    }

    final controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setBackgroundColor(const Color(0xFF121212))
      ..setNavigationDelegate(
        NavigationDelegate(
          onProgress: (p) {
            if (mounted) setState(() => _progress = p);
          },
          onPageStarted: (_) {
            if (mounted) setState(() => _loading = true);
            // Re-apply SSL trust in case WebView attached after first channel call.
            _sslChannel.invokeMethod('enableTrustSelfSigned').catchError((_) {});
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
            debugPrint('WebView error: ${err.errorCode} ${err.description}');
          },
        ),
      );

    if (controller.platform is AndroidWebViewController) {
      final android = controller.platform as AndroidWebViewController;
      await android.setMediaPlaybackRequiresUserGesture(false);
    }

    if (mounted) {
      setState(() => _controller = controller);
    }

    // Install native SSL proceed hook after the platform WebView exists.
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      try {
        await _sslChannel.invokeMethod('enableTrustSelfSigned');
      } catch (e) {
        debugPrint('SSL channel: $e');
      }
    });

    await controller.loadRequest(
      Uri.parse(widget.initialUrl),
      headers: headers,
    );
  }

  Future<bool> _handleBack() async {
    final c = _controller;
    if (c != null && await c.canGoBack()) {
      await c.goBack();
      return false;
    }
    return true;
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
              onPressed: controller == null ? null : () => controller.reload(),
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
