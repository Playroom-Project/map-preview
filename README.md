# PP Map PreView

**Map PreView previews Minecraft world generation and provides server-owned chunk pregeneration.**

This is the `1.20.x` branch. Fabric is implemented and tested as seven separate Minecraft 1.20–1.20.6 mods. Every target passed native generator tests, a Minecraft server GameTest and checks in a client loading the installable JAR. Forge, NeoForge and Quilt have prepared port descriptors. See [Fabric installation and controls](docs/fabric-1.20.x.md), [version pins](gradle/targets.json) and the exact scope of [validation](docs/validation-1.20.x.md).

## Identity

| Use | Value |
| --- | --- |
| Distribution title | PP Map PreView |
| Runtime name, logs and configuration | Map PreView |
| Java identifier | `MapPreView` |
| Machine ID and mod ID | `map_preview` |
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

Run Gradle with JDK 21, which Loom requires. Install JDK 17 as well for native tests of Minecraft 1.20–1.20.4. Shared libraries keep Java 17 bytecode; Minecraft 1.20.5 and 1.20.6 native adapters use Java 21.

```sh
./gradlew -PminecraftVersion=1.20.1 check assemble
python3 scripts/check_architecture.py
python3 scripts/verify_fabric_jar.py 1.20.1
```

On Windows use `gradlew.bat`. The checked-in Gradle wrapper is pinned to 8.14.3 and verifies its distribution checksum. Shared dependency locks and downloaded-dependency checksums are committed. Loom's locally generated mapping outputs are excluded from download checksums; their original dependencies remain verified. The core and all algorithm modules have no external runtime dependencies; JSON persistence isolates Gson in `config`.

The installable artifact is `build/fabric/<minecraft>/libs/map-preview-fabric-<minecraft>-<modVersion>.jar`. Select the exact Minecraft version in the command above. Development, source, config-internal and individual shared-library JARs are not installable mods. The config module's Gson is relocated privately; native Minecraft codecs keep Minecraft's own Gson types. Use `-Ploader=none` to build only shared libraries on Java 17 or 21.

Run the synthetic engine benchmark with:

```sh
./gradlew -Ploader=none :benchmarks:run --args="--iterations=5"
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

`versions/shared` contains the native Yarn adapter. `versions/legacy`, `versions/modern` and `versions/components` isolate native API changes. `platforms/fabric` owns Fabric event wiring, metadata and Loom packaging. Other loader directories provide exact port descriptors resolved by `scripts/prepare_target.py`; see [porting](docs/porting.md). Forge has no 1.20.5 release; NeoForge has no 1.20 release.

Native 3D, accurate cave blocks, verified structure starts, Tectonic configuration controls, FancyMenu bindings and third-party map/pregeneration integrations remain acceptance work. Their presence in the shared contracts is not a claim that their native adapters are implemented.

## Development references

- [Architecture and ownership](docs/architecture.md)
- [Requirements traced to both supplied PDFs](docs/requirements.md)
- [Adapter contracts and porting](docs/porting.md)
- [Compatibility status](docs/compatibility.md)
- [Configuration schema](docs/config.schema.json) and [example](docs/config.example.json)
- [Testing and benchmarks](docs/benchmarking.md)

No project distribution license has been selected. Third-party build components retain their own licenses; see [third-party notices](THIRD_PARTY_NOTICES.md).
