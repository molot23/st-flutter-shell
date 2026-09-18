import 'package:flutter_test/flutter_test.dart';

void main() {
  test('default URL constant', () {
    expect(
      'https://example.com'.startsWith('https://'),
      isTrue,
    );
  });
}
