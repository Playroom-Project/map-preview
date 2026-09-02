# PP Map PreView

**Map PreView is a modular foundation for a Minecraft world preview and pregeneration mod.**

This repository currently builds and tests the shared Java 17 libraries. It does **not** yet produce an installable Fabric, Forge, NeoForge or Quilt mod. Minecraft world generation, native screens, texture uploads and server chunk access are explicit adapter boundaries for the next implementation stage.

## Identity

| Use | Value |
| --- | --- |
| Distribution title | PP Map PreView |
| Runtime name, logs and configuration | Map PreView |
| Java identifier | `MapPreView` |
| Machine ID and future mod ID | `map_preview` |
| Java package root | `io.github.playroomproject.mappreview` |
| Repository | `Playroom-Project/map-preview` |

Spaces cannot appear in Java identifiers or loader IDs. Those technical spellings represent the runtime name; the `PP` prefix is reserved for distribution branding.

## What works in the base

- Primitive biome, height, surface, cave, slime and structure channel contracts, with explicit capability and accuracy reporting.
- A working tile engine with progressive LOD, center-first priority, one optional coarse prefetch ring, bounded outstanding work, deduplication and cooperative cancellation.
- Immutable generation epochs and comprehensive cache fingerprints, including datapack order, mod/configuration content, registry palettes and backend data revisions.
- A byte-budgeted LRU and a versioned, checksummed compressed raster codec.
- Namespaced biome colors, reusable height/topography/slope views, pure filters, camera transforms and a bounded render upload handoff.
- A terrain mesh builder that reuses height samples and shared tile edges without generating chunks.
- Streaming rectangle, circle and polygon pregeneration plans, row/spiral traversal, server-thread scheduling, pause/cancel/drain, retries, progress, save barriers and resumable checkpoints.
- Validated JSON configuration, hardware budgets, optional backups and atomic file replacement.
- Public backend, biome color, structure, dimension and configuration-preview extension APIs.

The synthetic generator is confined to test fixtures and benchmarks. It is not shipped in the production core and must never be presented as a Minecraft preview.

## Build and test

Use a JDK, not a JRE. Java 17 is the source and bytecode baseline; newer loader targets may run these libraries on newer JDKs.

```sh
./gradlew clean check assemble
python3 scripts/check_architecture.py
```

On Windows use `gradlew.bat`. The checked-in Gradle wrapper is pinned to 8.14.3 and verifies its distribution checksum. Dependency locks and checksum verification are committed. The core and all algorithm modules have no external runtime dependencies; JSON persistence isolates Gson in `config`.

Library artifacts are written to each module's `build/libs/`. These are development libraries, not loader-ready mod files. CI is configured to exercise the base on Java 17 and Java 21.

Run the synthetic engine benchmark with:

```sh
./gradlew :benchmarks:run --args="--iterations=5"
```

See [benchmark protocol](docs/benchmarking.md). No speedup over another mod or Minecraft compatibility is claimed without actual game measurements.

## Modules

| Module | Responsibility |
| --- | --- |
| `core` | Identities, sampling contracts, primitive tiles, LOD, cache, scheduling, colors and camera |
| `minecraft-common` | World-creation snapshots, registry lifetime and version bridge contracts |
| `client-common` | UI facade, CPU colorization, mesh construction and render-thread handoff |
| `pregen-common` | Chunk areas, traversal, server state machine and checkpoints |
| `config` | JSON schema, validation, hardware settings and atomic persistence |
| `compat/api` | Optional provider registration; no third-party mod imports |
| `platforms/common` | Loader services and the client composition root |
| `benchmarks` | Reproducible synthetic engine measurements |

`versions/` and the loader directories describe the intended adapter contracts. They are deliberately not empty mod artifacts or unsupported build targets. Fabric 1.20.1 with Yarn is the first planned game target. Cloche is the selected future target assembly layer; see [porting](docs/porting.md).

## Development references

- [Architecture and ownership](docs/architecture.md)
- [Requirements traced to both supplied PDFs](docs/requirements.md)
- [Adapter contracts and porting](docs/porting.md)
- [Compatibility status](docs/compatibility.md)
- [Configuration schema](docs/config.schema.json) and [example](docs/config.example.json)
- [Testing and benchmarks](docs/benchmarking.md)

No project distribution license has been selected. Third-party build components retain their own licenses; see [third-party notices](THIRD_PARTY_NOTICES.md).
