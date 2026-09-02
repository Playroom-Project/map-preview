#!/usr/bin/env python3
"""Check the distributable's version, class targets, remapping and private JSON packaging."""
import argparse
import json
import struct
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "io/github/playroomproject/mappreview/"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("minecraft")
    args = parser.parse_args()
    matrix = json.loads((ROOT / "gradle/targets.json").read_text())
    target = next(row for row in matrix["targets"] if row["minecraft"] == args.minecraft)
    version = next(line.split("=", 1)[1] for line in (ROOT / "gradle.properties").read_text().splitlines() if line.startswith("modVersion="))
    jar = ROOT / f"build/fabric/{args.minecraft}/libs/map-preview-fabric-{args.minecraft}-{version}.jar"
    with zipfile.ZipFile(jar) as archive:
        names = archive.namelist()
        assert len(names) == len(set(names)), "Duplicate ZIP entries"
        metadata = json.loads(archive.read("fabric.mod.json"))
        assert metadata["id"] == "map_preview" and metadata["name"] == "Map PreView"
        assert metadata["version"] == version
        assert metadata["depends"]["minecraft"] == args.minecraft
        assert metadata["depends"]["java"] == f">={target['java']}"
        assert metadata["depends"]["fabric-api"] == f">={target['fabric_api']}"
        assert "META-INF/licenses/Gson-Apache-2.0.txt" in names
        assert "META-INF/THIRD_PARTY_NOTICES.md" in names
        for side in ("main", "client"):
            assert len(metadata["entrypoints"][side]) == 1
            assert metadata["entrypoints"][side][0].replace(".", "/") + ".class" in names
        mixins = json.loads(archive.read("map_preview.mixins.json"))
        assert "CreateWorldScreenInvoker" in mixins["client"]
        assert mixins.get("refmap") in names, "Mixin refmap missing from distributable"
        refmap = json.loads(archive.read(mixins["refmap"]))
        assert "method_" in json.dumps(refmap), "Invoker was not remapped to intermediary"
        for relative in ("core/scheduler/TileEngine", "client/render/RenderUploadQueue", "pregen/PregenController",
                         "config/AtomicJsonStore", "internal/gson/Gson", "minecraft/client/MapPreViewScreen",
                         "minecraft/pregen/NativePregenBridge", "minecraft/worldgen/NativeWorldgenSampler"):
            assert PACKAGE + relative + ".class" in names, f"Missing runtime class: {relative}"
        assert not any(name.startswith(("net/minecraft/", "net/fabricmc/", "com/google/gson/", "org/junit/")) for name in names), "Game, loader, public Gson or tests were bundled"
        assert not any(any(marker in name for marker in ("SyntheticWorldgen", "NativeWorldgenTest", "NativePregenGameTest",
                                                         "NativeTestResources", "MapPreviewBuildProbe")) for name in names)
        json_store = archive.read(PACKAGE + "config/AtomicJsonStore.class")
        assert b"mappreview/internal/gson/" in json_store
        assert b"com/google/gson/" not in json_store, "Config still references Minecraft's Gson"
        native = archive.read(PACKAGE + "minecraft/worldgen/NativeWorldSnapshot.class")
        assert b"net/minecraft/class_" in native and b"com/google/gson/JsonElement" in native
        for name in names:
            if name.endswith(".class") and not name.startswith("META-INF/versions/"):
                major = struct.unpack(">H", archive.read(name)[6:8])[0]
                assert major <= target["java"] + 44, f"Java bytecode too new: {name} ({major})"
    print(f"Verified Fabric {args.minecraft}: {jar.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
