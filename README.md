# 家庭酒馆 / Family Tavern

Flutter Android WebView shell for **SillyTavern** behind **SakuraFrp** (or any HTTPS URL).

Package ID: `com.molot23.stshell`

---

## Features / 功能

| EN | 中文 |
|----|------|
| Dark launcher with title **家庭酒馆**, URL field, Connect, Settings | 深色启动页：标题、URL、连接、设置 |
| Default URL `https://103.91.208.41:23013` | 默认 URL 同上 |
| Settings: URL + Basic Auth user/password | 设置：URL 与 Basic Auth 账号密码 |
| URL → `shared_preferences` | URL 持久化到 SharedPreferences |
| Password + username → `flutter_secure_storage` | 凭据存入安全存储（不进仓库） |
| WebView injects `Authorization: Basic …` when credentials set | 有凭据时注入 Basic Auth 请求头 |
| Android trusts self-signed SSL (`onReceivedSslError` → proceed) | Android 信任自签名证书 |
| Back: WebView `canGoBack` then pop | 返回键：先网页后退，再退出页面 |

**Never commit passwords.** Credentials are entered in the app Settings screen only.

---

## Prerequisites / 环境

- [Flutter](https://docs.flutter.dev/get-started/install) 3.16+ (stable)
- Android SDK / Android Studio
- A device or emulator

```bash
flutter doctor
```

---

## Build APK / 打包 APK

```bash
git clone https://github.com/molot23/st-flutter-shell.git
cd st-flutter-shell
flutter pub get
flutter build apk --release
```

Output:

```text
build/app/outputs/flutter-apk/app-release.apk
```

Debug build:

```bash
flutter build apk --debug
# or run on a connected device:
flutter run
```

Install:

```bash
adb install -r build/app/outputs/flutter-apk/app-release.apk
```

---

## First launch / 首次使用

1. Open **家庭酒馆**
2. Confirm or edit the URL (default SakuraFrp endpoint)
3. Optional: **Settings** → enter Basic Auth username & password → Save
4. Tap **Connect**

---

## Project layout / 目录

```text
lib/
  main.dart
  screens/launcher_screen.dart
  screens/settings_screen.dart
  screens/webview_screen.dart
  services/settings_service.dart
android/app/src/main/kotlin/com/molot23/stshell/MainActivity.kt  # SSL trust hook
```

---

## Security notes / 安全说明

- No hardcoded password in source or config.
- `.gitignore` excludes `.env`, keystores, and `*.apk`.
- Self-signed SSL proceed is intentional for FRP tunnels — only use against hosts you trust.
- Release APK in this repo’s Gradle uses **debug signing** for convenience; replace with your own keystore before distributing widely.

---

## License

Private / personal use unless otherwise noted by the repo owner.
