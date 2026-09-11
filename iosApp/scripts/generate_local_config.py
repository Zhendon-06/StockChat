"""Generate Debug-only iOS resources; never print or write credentials to source."""
import os
import plistlib
import re
from pathlib import Path


KEYS = ("QWEN_API_KEY", "MIMO_VOICE_API_KEY", "AI_PROXY_BASE_URL", "AI_PROXY_TOKEN")


def read_properties(path):
    values = {}
    if not path.is_file():
        return values
    pending = ""
    for line in path.read_text(encoding="iso-8859-1").splitlines():
        line = pending + line.lstrip()
        slash_count = len(line) - len(line.rstrip("\\"))
        if slash_count % 2:
            pending = line[:-1]
            continue
        pending = ""
        if not line or line.startswith(("#", "!")):
            continue
        match = re.match(r"(QWEN_API_KEY|MIMO_VOICE_API_KEY|AI_PROXY_BASE_URL|AI_PROXY_TOKEN)(?:\s*[=:]\s*|\s+)(.*)$", line)
        if match:
            value = re.sub(
                r"\\u([0-9a-fA-F]{4})|\\(.)",
                lambda m: chr(int(m[1], 16)) if m[1] else {
                    "t": "\t", "n": "\n", "r": "\r", "f": "\f"
                }.get(m[2], m[2]),
                match[2],
            )
            values[match[1]] = value.strip()
    return values


def generate(environment):
    values = {}
    if environment.get("CONFIGURATION") == "Debug":
        local = read_properties(Path(environment["SRCROOT"]).parent / "local.properties")
        for key in KEYS:
            value = environment.get(key, "").strip() or local.get(key, "")
            if value:
                values[key] = value
    output = (Path(environment["TARGET_BUILD_DIR"])
              / environment["UNLOCALIZED_RESOURCES_FOLDER_PATH"] / "StockChatLocalConfig.plist")
    output.parent.mkdir(parents=True, exist_ok=True)
    # Always overwrite, including Release, so stale Debug keys cannot survive.
    output.write_bytes(plistlib.dumps(values))
    output.chmod(0o600)


if __name__ == "__main__":
    generate(os.environ)
