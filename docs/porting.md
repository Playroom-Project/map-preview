# Map PreView adapter implementation guide

## First target

The first intended game target is **Fabric, Minecraft 1.20.1, Yarn mappings, Java 17**. No loader/version target is enabled in this base increment. Adding an empty loader metadata file would misleadingly create a mod that loads but cannot preview anything, so the repository currently emits shared development libraries only.

Use [Cloche](https://github.com/terrarium-earth/cloche) as the target assembly layer when implementing the first real target. Its target model separates Minecraft versions and loaders, and it generates loader metadata. Read and pin a released plugin version and the relevant mapping/loader/API coordinates in that change. Do not use dynamic versions or combine Cloche, Architectury, Stonecutter and another multi-loader layer without a demonstrated need.

The base uses ordinary Gradle Java modules so a developer can build and test algorithms without downloading Minecraft. Cloche belongs in the target assembly project, not in `core`. The platform PDF explicitly permits separate platform modules; this follows that option and the longer report's preference for small interfaces with Cloche.

## Where new code belongs

| Location | Code allowed there |
| --- | --- |
| `core` | Version-independent algorithms and primitive data only |
| `minecraft-common` | Mappings-free registry/world-creation interfaces |
| `client-common` | Mappings-free rendering/UI data and CPU transformations |
| `pregen-common` | Native-server interfaces and scheduling state machine |
| `versions/mc-1.20.1` | Yarn 1.20.1 generator, registries, structure and native rendering implementation |
| `platforms/fabric` | Fabric bootstrap, events, paths, integration discovery and client/server lifecycle hooks |
| `platforms/forge`, `platforms/neoforge`, `platforms/quilt` | Equivalent loader-specific bindings for supported version combinations |
| `compat/<mod>/<version-family>` | Optional, version-checked third-party API bindings |

Cloche source sets can point at these adapter directories. Select actual supported Minecraft/loader pairs; Forge and NeoForge are not interchangeable merely because both use TOML metadata. Shared libraries stay at Java 17 even if a future target requires Java 21 or later.

## Required worldgen bridge behavior

1. Capture the existing vanilla world-creation state and ordered active datapacks. Bootstrap a detached snapshot rather than mutating an active world.
2. Enumerate all world stems/dimensions from that snapshot. Build a `PreviewContext` per dimension and a stable biome palette from its registries.
3. Fingerprint actual generator settings, datapack content/order, worldgen-affecting mod content/versions and configuration snapshots. Include state-palette identity for block channels.
4. Return a `BackendFactory` using the actual `BiomeSource`, sampler and `ChunkGenerator`. Convert core block coordinates to native quart/chunk coordinates with floor division.
5. Advertise only operations that are actually available for that generator. Dummy height/column implementations cannot be marked as accurate. Distinguish candidates, raw samples and verified structures.
6. Start with serial access. Increase concurrency only after proving sampler isolation or safety for the selected generator. A factory's `open`, sample and private cleanup run on its worker.
7. Keep registry resources leased until every sampler using them has closed. Late snapshot completions from cancelled UI edits must be discarded and released.
8. Add a specialized noise/Tectonic path only after parity tests. Bump backend `dataVersion` whenever output semantics or local state encoding changes.

Core biome sampling takes an explicit Y. The UI must label that slice correctly; do not describe it as a decorated surface sample. Seed text parsing and empty/random seed behavior must use the selected Minecraft version's vanilla semantics.

## Client and renderer

- Use stable screen packages under `io.github.playroomproject.mappreview`, with a composable canvas and ordinary native widget behavior where possible.
- Attach to vanilla Create World through public hooks/events first, then narrow accessors/invokers or mixins only where necessary. Return selected configuration to vanilla creation.
- Feed planner requests in priority order. Retry saturated visible work later. Use `retainRequests` to cancel work outside the latest viewport plus allowed prefetch.
- Schedule expensive colorization off the render thread, then hand immutable ARGB data to `RenderUploadQueue` with both generation epoch and display revision.
- Allocate/upload/draw/release GPU resources on the render thread. Use batched texture draws and a separate bounded GPU cache. Keep coarse tiles visible during refinement.
- Native mesh rendering must manage materials, water, LOD transitions, camera controls and GPU buffer lifetime. `TerrainMesh` supplies only coarse geometry and shared edge samples.
- FancyMenu is optional. A version bridge may bind the stable `PreviewUiApi` and state fields to documented actions. Verify actual screen customization and the relevant FancyMenu package blocklist in game.

## Server pregeneration

- Implement `PregenBridge` with the logical or integrated server's scheduler and native chunk tickets. Do not generate world chunks in the preview pool.
- Complete chunk futures after FULL generation and submission to the native saving pipeline. Complete `flush()` only after native saves required for a checkpoint/completion have finished.
- Native ticket close must be idempotent and release only the controller's own ticket. Never cancel or corrupt unrelated worldgen futures.
- Call `tick`, control methods and checkpoint requests on the server thread. Completion callbacks may originate elsewhere; the controller marshals them through its completion queue.
- Build very large area plans on an appropriate background planning path. Rectangle counts are constant-time; circle/polygon counts depend on their row/edge geometry.
- Closing the progress UI does not stop the service. Joining the world invokes cancellation and must observe the server's existing world lifecycle. Expose the requested unfinished-world resume/enter prompt.
- Persist a checkpoint only after `checkpoint()` completes and before resuming admission. Restore checks shape/world identity, exact accepted prefix count and unfinished dispatched positions.
- Supply a stable native world/save identity in addition to worldgen settings so two different saves with the same seed do not share a checkpoint identity.

## Optional integrations and settings

The loader discovers optional mods without loading absent classes. Register providers before freezing `PreviewExtensions`, honor `disabledIntegrations`, and map unsupported versions to an explicit unavailable capability. Never assume that all releases of a mod for one Minecraft version expose the same API.

Tectonic controls operate on a cloned version-specific configuration. Debounce slider edits, validate the clone, fingerprint it and rebuild the detached preview session. An explicit Apply action checks the original configuration, backs it up and writes atomically. Do not write the live config on slider ticks or silently change another optimization mod's global settings.

Map and Distant Horizons adapters consume completed native chunks through public versioned APIs. They must not guess private file formats or call a rough preview tile a fully saved chunk/LOD artifact. Chunky can implement the native pregeneration boundary instead of introducing another chunk scheduler.

GPU compute remains an experimental provider requiring capability checks, correctness parity, resource limits and CPU fallback. A GPU vendor string, Nvidium installation or shader feature flag alone does not prove compatible world generation.

## Gates before labeling a target supported

- Its Cloche build assembles real loader metadata and a client/server-safe artifact with runtime name `Map PreView`, machine ID `map_preview` and the requested distribution title.
- Headless shared tests pass, plus native registry/world-creation lifecycle tests and sample parity against generated worlds at fixed positive and negative coordinates.
- Seed/dimension/config changes cancel stale work, release snapshots and GPU resources, and never write live worldgen state during preview edits.
- Native pregeneration survives pause/cancel/save/restart and coexists with the chosen scheduler/modpack.
- The compatibility matrix and benchmark report identify the exact Minecraft, loader, Java, mods, settings, seed, viewport and hardware tested.
