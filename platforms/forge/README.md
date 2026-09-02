# Forge binding

Status: prepared port. `target.json` records repository, coordinate template, mapping family, metadata location and native event hooks. Exact pins are in `gradle/targets.json`. Forge has no upstream 1.20.5 target.

Run `python3 scripts/prepare_target.py forge 1.20.1` from the repository root to generate resolved developer properties. Implement Forge bootstrap, configuration paths, discovery and event bindings here. An official-mapping native adapter must implement the shared interfaces before this target can be built or distributed. The Fabric Yarn adapter is the behavioral reference, and its code cannot be directly compiled with official names. See `docs/porting.md` for the acceptance gates.
