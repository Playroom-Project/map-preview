# Map PreView requirements traceability

Sources supplied with the task:

- **R:** `PP Map PreView.pdf`, technical research report, 36 pages.
- **P:** `Plattform og modularkitektur.pdf`, platform and module architecture, 5 pages.

This branch extends the shared base with native Fabric adapters for the 1.20 series. **Implemented** describes code, while [validation results](validation-1.20.x.md) record what was actually executed. **Contract** means that a typed boundary exists but its adapter is still required. **Later** identifies remaining application or integration work. No label implies general modpack compatibility.

| Requirement and source | Base location | Status and remaining work |
| --- | --- | --- |
| Runtime naming and English code descriptions; task instruction | `MapPreView`, configuration, manifests, all source files | Implemented; distribution branding remains separate |
| Shared core with loader/version separation; P1, R24-28 | Shared modules, `versions/`, `platforms/fabric` | Implemented; narrow version families and one centralized target matrix |
| Fabric, Forge, NeoForge and Quilt path; P1, R26-28 | `gradle/targets.json`, loader descriptors, `prepare_target.py` | Fabric implemented; other loaders prepared; nonexistent upstream pairs rejected |
| Actual active registries/generator, generic path, verified fast path; P1, R1-6, R12 | `NativeWorldSnapshot`, `NativeWorldgenSampler` | Native codec clones and active registries implemented; no speculative fast path |
| Dynamic dimensions and biome registry IDs; P1, R4-6 | `NativeWorldSnapshot`, `BiomePalette` | Native dimension enumeration and biome palette implemented |
| Separate sampling/renderer, tiled zoom pyramid; P1, R6-14 | `PreviewCanvas`, `AtlasPreviewRenderer`, `ViewportPlanner` | Native 2D texture atlas and progressive LOD implemented |
| Coarse-first visible work, center priority, bounded prefetch; R6-8, R12-14 | `WorkPriority`, `ViewportPlanner` | Implemented |
| Async generation, cancellation, seed/config epochs; R7, R11 | `TileEngine`, `PreviewSession` | Implemented and regression tested |
| Lazy layers and no hidden expensive sampling; P1, R8-9, R15-16 | `DataLayer`, `BackendCapabilities`, `TileSampling` | Implemented dispatch and contracts; unavailable channels fail explicitly |
| Full cache identity including ordered packs/mods/config/Tectonic/Y; R9-10 | `WorldgenEnvironment`, `PreviewContext`, `TileKey` | Shared identity contract implemented; native registries/generators, pack order, mod versions and editor sessions included; arbitrary external mod configuration providers remain later |
| Primitive buffers, bounded LRU and compression; P4, R10-11 | `RasterTile`, `TileCache`, `RasterTileCodec` | Implemented; persistent disk eviction is later |
| Worker/render-thread boundary and batched texture rendering; R10-11, R30 | `RenderUploadQueue`, `AtlasPreviewRenderer` | Implemented; bounded coloring and uploads, 16 MiB GPU atlas |
| Namespaced HEX colors and tag/environment/fallback colors; P1, R14-15 | `BiomeColors`, `PreviewConfig` | Implemented |
| Filters and recoloring without regenerating world data; P1, R14-15 | `PreviewCanvas`, `TileColorizer`, `PreviewSettings` | Native biome filter and saved HEX overrides implemented |
| Height, topography, slope and land/ocean; P1, R8, R15 | `NativeWorldgenSampler`, halo tiles, `TileColorizer` | Native raw-height views implemented; decoration remains outside their semantics |
| Candidate versus verified structures; P1, R6, R8 | `NativeStructureSampler`, `StructureTile` | Native placement candidates implemented and labeled estimated; verification later |
| Cave biome, density and accurate block modes; P1, R15-16 | `NativeWorldgenSampler`, layer capabilities | Cave biome slices and raw density implemented; accurate block mode later |
| Seed parsing/randomization and existing vanilla Create flow; P1, P3, R13, R16-17 | `MapPreViewScreen`, `CreateWorldScreenInvoker` | Native seed semantics, debounce, cancellation and vanilla creation implemented |
| Server pregeneration separated from preview; P2, R17-18 | `NativePregenBridge`, `NativePregenService` | Native FULL futures, owned tickets and server lifecycle implemented |
| Rectangle, circle, free-form polygon, spiral; P2, P4 | `ChunkAreas`, `ChunkPlan` | Implemented streaming shapes and traversal; hardware-specific order tuning requires profiling |
| In-flight budget, C2ME/native scheduler cooperation; P2-4, R17-18 | `NativePregenBridge`, bounded cursor polling | Native scheduling implemented; actual C2ME modpack verification later |
| Progress, actual/average rate, totals, percent and ETA; P2 | `PregenProgress`, `PregenerationScreen`, commands | Native progress panel and commands implemented; geographic progress overlay later |
| Pause/cancel/background, world-join cancellation and resume; P2 | `NativePregenService`, server controls | Pause/cancel/drain/explicit saved resume implemented; pre-entry resume dialog later |
| Safe save barriers, retries and checkpoint persistence; P2, P3 | Native `saveAll`, checkpoint and atomic store | Implemented; native integration test exercises pause, cancel, restore and final saving |
| Chunky as optional backend; R18, R34 | Native service boundary and `compat/chunky` plan | Contract; no third-party internals or duplicated chunk scheduler |
| Distant Horizons and map mods; P2 | `GeneratedChunkListener`, compatibility provider plan | Contract; versioned public API adapters remain later |
| Rough 3D from heightfield, near/far LOD and camera; P2, R18-19 | `TerrainMesh`, existing LOD/camera/render contracts | CPU mesh implemented; fly camera, water, materials and native 3D are later |
| Tectonic detached live edits, debounce, presets and explicit Apply; P3, R19-22 | `ConfigPreviewProvider`, context fingerprints, atomic backup persistence | Contract; versioned schema/bootstrap, debounce controls and native UI are later |
| FancyMenu optional and composable UI; P3-4, R22-24 | `PreviewUiApi`, `PreviewUiState`, renderer boundary | Contract; actual widgets, actions and package-blocklist validation are later |
| C2ME, Lithium, ModernFix, Nvidium and worldgen families; P3, R4-5, R31 | `LoaderPlatform`, provider capabilities and compatibility matrix | Contract; no compatibility claims before real modpack tests |
| Hardware profiles and Eco/Balanced/Fast/Max budgets; P4, R12-13 | `HardwareProfile`, `PerformanceMode`, `PreviewConfig` | Implemented JVM budgets and persistence; render-thread GPU probe is a contract |
| GPU texture/mesh work and optional OpenCL/CUDA worldgen; P2, P4 | Render/compute adapter boundaries | Later; no unverified GPU compute path or GPU-vendor heuristic is enabled |
| JSON settings and per-setting documentation; P4 | `AtomicJsonStore`, schema and example | Implemented; native Cloth Config/loader widgets are later |
| Public backend/color/structure/config/dimension API; R29 | `PreviewExtensions`, immutable provider registries | Implemented registration and contracts |
| Actual-world parity and named modpack matrix; R30-32 | Native JUnit, server GameTest and packaged client checks | All seven vanilla Fabric targets passed generator/server/client checks; 1.20.6 creation and integrated pregeneration passed; third-party modpacks remain untested |
| Reproducible performance measurements and no unsupported speed claims; P4-5, R32-33 | `benchmarks`, benchmark protocol | Synthetic base benchmark implemented; Minecraft/FPS/comparative metrics are later |
| Purpose, API, performance and integration documentation; P4-5 | Architecture, porting, schema, traceability and benchmark docs | Implemented for the base and its remaining boundaries |

The source PDFs contain illustrative and sometimes conflicting recommendations. The architecture document records the resulting choices instead of claiming that speculative APIs or experimental GPU paths are already implemented.
