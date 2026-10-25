import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('apk_version', Path(__file__).parents[1] / 'tools/set-apk-version.py')
apk_version = importlib.util.module_from_spec(spec)
spec.loader.exec_module(apk_version)

class VersionTests(unittest.TestCase):
    def test_successive_builds_keep_metadata_and_increase_version(self):
        with tempfile.TemporaryDirectory() as folder:
            p = Path(folder) / 'apktool.yml'
            p.write_text('versionInfo:\n  versionCode: 60\n  versionName: 5.1.0\nkeep: original\n')
            apk_version.set_version(p, '100010', '0.1.10')
            self.assertIn('versionCode: 100010', p.read_text())
            apk_version.set_version(p, '100011', '0.1.11')
            self.assertIn('versionCode: 100011', p.read_text())
            self.assertIn('versionName: 0.1.11', p.read_text())
            self.assertIn('keep: original', p.read_text())

    def test_invalid_codes_and_missing_metadata_fail_without_writing(self):
        with tempfile.TemporaryDirectory() as folder:
            p = Path(folder) / 'apktool.yml'
            original = 'versionInfo:\n  versionCode: 60\n  versionName: 5.1.0\n'
            for code in ['', '0', '-1', '2100000001', 'abc']:
                p.write_text(original)
                with self.assertRaises(ValueError): apk_version.set_version(p, code, '0.1.1')
                self.assertEqual(p.read_text(), original)
            p.write_text('versionInfo:\n  versionCode: 60\n')
            with self.assertRaises(ValueError): apk_version.set_version(p, '100001', '0.1.1')
            self.assertEqual(p.read_text(), 'versionInfo:\n  versionCode: 60\n')

if __name__ == '__main__': unittest.main()
