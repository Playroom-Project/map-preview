# Quilt binding

Status: prepared port. `target.json` records the loader coordinate, repository, Yarn mapping family, metadata and entrypoint contracts. The Minecraft matrix covers 1.20 through 1.20.6.

Run `python3 scripts/prepare_target.py quilt 1.20.1` from the repository root to generate resolved developer properties. Select and pin compatible QSL/QFAPI releases before implementing lifecycle and screen bindings. The native Yarn source sets and shared libraries provide the porting boundary; Fabric entrypoints must be replaced by explicit Quilt bindings.

No Quilt artifact is produced yet. Fabric compatibility alone is not evidence of a tested Quilt build. Follow the bootstrap, game, save and rendering checks in `docs/porting.md` before enabling distribution.
