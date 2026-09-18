import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Persists URL in SharedPreferences; username/password in secure storage.
class SettingsService {
  SettingsService._();
  static final SettingsService instance = SettingsService._();

  static const String defaultUrl = 'https://103.91.208.41:23013';
  static const String _keyUrl = 'st_shell_url';
  static const String _keyUser = 'st_shell_basic_user';
  static const String _keyPass = 'st_shell_basic_pass';

  late SharedPreferences _prefs;
  final FlutterSecureStorage _secure = const FlutterSecureStorage(
    aOptions: AndroidOptions(encryptedSharedPreferences: true),
  );

  Future<void> init() async {
    _prefs = await SharedPreferences.getInstance();
  }

  String get url => _prefs.getString(_keyUrl) ?? defaultUrl;

  Future<void> setUrl(String value) async {
    await _prefs.setString(_keyUrl, value.trim());
  }

  Future<String> getUsername() async {
    return await _secure.read(key: _keyUser) ?? '';
  }

  Future<String> getPassword() async {
    return await _secure.read(key: _keyPass) ?? '';
  }

  Future<void> setCredentials({
    required String username,
    required String password,
  }) async {
    if (username.isEmpty && password.isEmpty) {
      await _secure.delete(key: _keyUser);
      await _secure.delete(key: _keyPass);
    } else {
      await _secure.write(key: _keyUser, value: username);
      await _secure.write(key: _keyPass, value: password);
    }
  }

  Future<bool> hasCredentials() async {
    final u = await getUsername();
    final p = await getPassword();
    return u.isNotEmpty || p.isNotEmpty;
  }
}
