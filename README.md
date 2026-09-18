# 家庭酒馆 / Family Tavern

Flutter Android WebView client for a self-hosted SillyTavern instance.

## Features

- Dark launcher: URL, Connect, Settings
- Save URL locally; store Basic Auth credentials in secure storage (never commit them)
- Inject Basic Auth when credentials are set
- Allow self-signed HTTPS (for reverse-proxy / tunnel setups you control)
- Android back: WebView history first, then leave the page

## Prerequisites

- Flutter 3.16+ (stable)
- Android SDK

```bash
flutter doctor
```

## Build APK

```bash
git clone https://github.com/molot23/st-flutter-shell.git
cd st-flutter-shell
flutter pub get
flutter build apk --release
```

APK output:

```text
build/app/outputs/flutter-apk/app-release.apk
```

```bash
adb install -r build/app/outputs/flutter-apk/app-release.apk
```

## First launch

1. Open the app
2. Enter your SillyTavern HTTPS URL in Settings (or on the launcher)
3. Optional: Basic Auth username & password → Save
4. Tap Connect

Do not put real passwords or private tunnel URLs into the repository.

## Layout

```text
lib/
  main.dart
  screens/launcher_screen.dart
  screens/settings_screen.dart
  screens/webview_screen.dart
  services/settings_service.dart
android/.../MainActivity.kt   # SSL trust hook
```

## Security

- No credentials in source
- `.gitignore` excludes `.env`, keystores, and `*.apk`
- Self-signed SSL bypass is only for hosts you trust
- Release builds currently use debug signing; use your own keystore before wide distribution

## License

Personal use unless the repo owner states otherwise.
