#!/usr/bin/env python3
"""Update apktool's version metadata without reformatting its YAML."""
import os
from pathlib import Path
import re
import sys


def set_version(path, code, name):
    if not re.fullmatch(r"[1-9][0-9]*", code) or int(code) > 2100000000:
        raise ValueError("SUPER_VERSION_CODE must be an integer from 1 to 2100000000")
    if not re.fullmatch(r"[A-Za-z0-9.+_-]+", name):
        raise ValueError("SUPER_VERSION_NAME must be a simple version label")
    text = path.read_text()
    for key, value in (("versionCode", code), ("versionName", name)):
        text, count = re.subn(r"(?m)^(  " + key + r":).*$", lambda m: m[1] + " " + value, text)
        if count != 1:
            raise ValueError("Expected exactly one apktool " + key)
    path.write_text(text)


if __name__ == "__main__":
    try:
        set_version(Path(sys.argv[1]), os.environ.get("SUPER_VERSION_CODE", ""),
                    os.environ.get("SUPER_VERSION_NAME", ""))
    except (ValueError, IndexError) as exc:
        sys.exit(str(exc))
