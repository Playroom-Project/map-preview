#!/usr/bin/env python3
"""Verify the base dependency boundary, runtime identity and trusted build entry point."""

from pathlib import Path
import hashlib
import json
import re
import sys
import zipfile


ROOT = Path(__file__).resolve().parents[1]
PREFIX = "io.github.playroomproject.mappreview."
MODULES = {
    "core": ("core", {"core"}),
    "minecraft-common": ("minecraft-common", {"core", "minecraft"}),
    "client-common": ("client-common", {"core", "client"}),
    "pregen-common": ("pregen-common", {"core", "pregen"}),
    "config": ("config", {"core", "pregen", "config"}),
    "compat-api": ("compat/api", {"core", "minecraft", "client", "pregen", "compat"}),
    "platform-api": ("platforms/common", {"core", "minecraft", "client", "pregen", "config", "compat", "platform"}),
    "benchmarks": ("benchmarks", {"core", "client", "testing", "benchmark"}),
}
FORBIDDEN = re.compile(r"\b(?:net\.minecraft\.|net\.fabricmc\.|net\.minecraftforge\.|net\.neoforged\.|org\.quiltmc\.|org\.lwjgl\.)")
WRAPPER_SHA256 = "7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172"
DISTRIBUTION_SHA256 = "bd71102213493060956ec229d946beee57158dbd89d0e62b91bca0fa2c5f3531"


def main() -> int:
    errors = []
    source_count = 0
    native_count = 0
    for module, (directory, permitted) in MODULES.items():
        module_root = ROOT / directory
        if not (module_root / "build.gradle.kts").is_file():
            errors.append(f"Missing build for {module}")
        for source in (module_root / "src/main/java").rglob("*.java"):
            source_count += 1
            text = source.read_text(encoding="utf-8")
            path = source.relative_to(ROOT)
            if FORBIDDEN.search(text):
                errors.append(f"Game, loader or graphics implementation leaked into {path}")
            if "PP Map PreView" in text or "PPMap" in text:
                errors.append(f"Distribution prefix leaked into runtime code: {path}")
            for imported in re.findall(r"^import\s+(?:static\s+)?([\w.*]+);", text, re.MULTILINE):
                if imported.startswith(PREFIX):
                    owner = imported[len(PREFIX):].split(".")[0]
                    if owner not in permitted:
                        errors.append(f"Dependency inversion in {path}: {imported}")
                elif module == "core" and not imported.startswith("java."):
                        errors.append(f"External runtime dependency in core: {imported}")

    loader_import = re.compile(r"\b(?:net\.fabricmc\.|net\.minecraftforge\.|net\.neoforged\.|org\.quiltmc\.)")
    for family in (ROOT / "versions").iterdir():
        for source in (family / "src/main/java").rglob("*.java"):
            native_count += 1
            text = source.read_text(encoding="utf-8")
            if loader_import.search(text):
                errors.append(f"Loader implementation leaked into native adapter: {source.relative_to(ROOT)}")
            if "PP Map PreView" in text or "PPMap" in text:
                errors.append(f"Distribution prefix leaked into native code: {source.relative_to(ROOT)}")
    for source in (ROOT / "platforms/fabric/src/main/java").rglob("*.java"):
        text = source.read_text(encoding="utf-8")
        if "PP Map PreView" in text or "PPMap" in text:
            errors.append(f"Distribution prefix leaked into Fabric code: {source.relative_to(ROOT)}")

    wrapper = ROOT / "gradle/wrapper/gradle-wrapper.jar"
    if not wrapper.is_file() or hashlib.sha256(wrapper.read_bytes()).hexdigest() != WRAPPER_SHA256:
        errors.append("Gradle wrapper differs from the verified 8.14.3 distribution")
    properties = (ROOT / "gradle/wrapper/gradle-wrapper.properties").read_text(encoding="utf-8")
    if f"distributionSha256Sum={DISTRIBUTION_SHA256}" not in properties:
        errors.append("Gradle distribution checksum is missing or changed without review")

    schema = json.loads((ROOT / "docs/config.schema.json").read_text(encoding="utf-8"))
    example = json.loads((ROOT / "docs/config.example.json").read_text(encoding="utf-8"))
    if set(example) != set(schema["required"]) or example.get("name") != "Map PreView":
        errors.append("Configuration example and schema are inconsistent")

    for jar in (ROOT / "core/build/libs").glob("*.jar"):
        if "test-fixtures" in jar.name or "sources" in jar.name:
            continue
        with zipfile.ZipFile(jar) as archive:
            if any("/testing/" in name or "SyntheticWorldgen" in name for name in archive.namelist()):
                errors.append(f"Synthetic worldgen leaked into production artifact {jar.name}")

    for document in [ROOT / "README.md", *(ROOT / "docs").glob("*.md")]:
        for target in re.findall(r"\]\(([^)]+)\)", document.read_text(encoding="utf-8")):
            if "://" in target or target.startswith("#"):
                continue
            target = target.split("#", 1)[0]
            if target and not (document.parent / target).exists():
                errors.append(f"Broken documentation link: {document.relative_to(ROOT)} -> {target}")

    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(f"Architecture verified: {len(MODULES)} shared modules, {source_count} shared and {native_count} native Java files, checked wrapper and documentation links.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
