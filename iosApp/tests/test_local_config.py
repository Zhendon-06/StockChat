import importlib.util
import plistlib
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location(
    "local_config", Path(__file__).parents[1] / "scripts/generate_local_config.py"
)
config = importlib.util.module_from_spec(spec)
spec.loader.exec_module(config)


class LocalConfigTests(unittest.TestCase):
    def test_debug_precedence_and_release_removes_credentials(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "local.properties").write_text(
                "QWEN_API_KEY=local-test-value\nMIMO_VOICE_API_KEY=voice-test-value\n"
            )
            environment = {
                "SRCROOT": str(root / "iosApp"),
                "TARGET_BUILD_DIR": str(root / "build"),
                "UNLOCALIZED_RESOURCES_FOLDER_PATH": "Test.app",
                "CONFIGURATION": "Debug",
                "QWEN_API_KEY": " environment-test-value ",
                "MIMO_VOICE_API_KEY": " ",
            }
            output = root / "build/Test.app/StockChatLocalConfig.plist"
            config.generate(environment)
            self.assertEqual(plistlib.loads(output.read_bytes()), {
                "QWEN_API_KEY": "environment-test-value",
                "MIMO_VOICE_API_KEY": "voice-test-value",
            })
            environment["CONFIGURATION"] = "Release"
            config.generate(environment)
            self.assertEqual(plistlib.loads(output.read_bytes()), {})

    def test_missing_local_config_and_properties_escaping(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "local.properties"
            self.assertEqual(config.read_properties(path), {})
            path.write_text("# comment\nQWEN_API_KEY : test\\u002dvalue\\\n  -continued\n")
            self.assertEqual(config.read_properties(path), {"QWEN_API_KEY": "test-value-continued"})


if __name__ == "__main__":
    unittest.main()
