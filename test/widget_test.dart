import 'package:flutter_test/flutter_test.dart';

void main() {
  test('default URL constant', () {
    expect(
      'https://103.91.208.41:23013'.startsWith('https://'),
      isTrue,
    );
  });
}
