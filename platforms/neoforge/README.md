# NeoForge binding

Status: prepared port. `target.json` records repositories, coordinates, mapping family, metadata and native event hooks. Exact pins are in `gradle/targets.json`. NeoForge has no upstream Minecraft 1.20 target.

Run `python3 scripts/prepare_target.py neoforge 1.20.6` from the repository root to generate resolved developer properties. The 1.20.1 target uses the legacy `net.neoforged:forge` coordinate; newer targets use `net.neoforged:neoforge`. Metadata changes from `META-INF/mods.toml` to `META-INF/neoforge.mods.toml` in the 1.20.5 family. The matrix preserves upstream beta version labels.

Implement bootstrap and event bindings here, with an official-mapping native adapter behind the shared interfaces. The Fabric Yarn implementation is the behavioral reference. Shared libraries remain Java 17-compatible; 1.20.5/1.20.6 require Java 21. No NeoForge artifact is produced until the native port and acceptance checks in `docs/porting.md` are complete.
