#!/usr/bin/env python3
"""Resolve an exact loader port target without manufacturing an installable placeholder mod."""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def resolve(loader: str, minecraft: str) -> dict:
    matrix = json.loads((ROOT / "gradle/targets.json").read_text())
    row = next((target for target in matrix["targets"] if target["minecraft"] == minecraft), None)
    if row is None:
        raise ValueError(f"Unsupported Minecraft version: {minecraft}")
    if loader in ("forge", "neoforge") and row[loader] is None:
        raise ValueError(f"{loader} has no upstream release for Minecraft {minecraft}")
    descriptor = json.loads((ROOT / f"platforms/{loader}/target.json").read_text())
    values = dict(matrix, **row)
    coordinate = descriptor["coordinate"]
    metadata = descriptor["metadata"]
    if loader == "neoforge":
        if minecraft == "1.20.1":
            coordinate = descriptor["legacy_1_20_1_coordinate"]
            descriptor["build_plugin"] = "net.minecraftforge.gradle"
        if minecraft in ("1.20.1", "1.20.2", "1.20.3", "1.20.4"):
            metadata = "META-INF/mods.toml"
    return dict(descriptor, minecraft=minecraft, java=row["java"],
                dependency=coordinate.format_map(values), metadata=metadata,
                native_source_family=row["api_family"], yarn=row["yarn"],
                shared_modules=["core", "minecraft-common", "client-common", "pregen-common", "config", "compat-api", "platform-api"])


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("loader", choices=("forge", "neoforge", "quilt"))
    parser.add_argument("minecraft")
    args = parser.parse_args()
    try:
        target = resolve(args.loader, args.minecraft)
    except ValueError as error:
        parser.error(str(error))
    directory = ROOT / "build/prepared" / args.loader / args.minecraft
    directory.mkdir(parents=True, exist_ok=True)
    (directory / "target.json").write_text(json.dumps(target, indent=2) + "\n")
    properties = {"minecraftVersion": args.minecraft, "javaVersion": target["java"],
                  "loaderDependency": target["dependency"], "modId": "map_preview", "modName": "Map PreView"}
    (directory / "gradle.properties").write_text("".join(f"{key}={value}\n" for key, value in properties.items()))
    print(f"Prepared {args.loader} {args.minecraft}: {directory.relative_to(ROOT)}")
    print("Status: porting target. Native event wiring and loader packaging must be implemented and validated before distribution.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
