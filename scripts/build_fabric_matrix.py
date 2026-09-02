#!/usr/bin/env python3
"""Build exact Fabric targets sequentially and verify each installable JAR."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def main() -> int:
    versions = [target["minecraft"] for target in json.loads((ROOT / "gradle/targets.json").read_text())["targets"]]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--versions", nargs="+", choices=versions, default=versions)
    parser.add_argument("--game-tests", action="store_true", help="Also run native server integration tests in isolated per-version directories")
    args = parser.parse_args()
    wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    for version in args.versions:
        command = [str(wrapper), "--no-daemon", f"-PminecraftVersion={version}", "check", "assemble"]
        if args.game_tests:
            command += ["-PgameTests", ":fabric:runGameTest"]
        subprocess.run(command, cwd=ROOT, check=True)
        subprocess.run([sys.executable, str(ROOT / "scripts/verify_fabric_jar.py"), version], cwd=ROOT, check=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
