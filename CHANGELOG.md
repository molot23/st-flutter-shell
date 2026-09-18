# Changelog

## 0.1.2+3

- Android WebView file chooser: enable SillyTavern “导入卡” (character card import) via `file_picker` + `setOnShowFileSelector`.
- Add `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` (maxSdk 32) for picking card images/JSON.
- Use `file_picker` ^10 and Android `compileSdk` 36 so WebView file import builds cleanly.

## 0.1.1+2

- Load ST CSS/JS in WebView via SSL + Basic Auth for subresources.
