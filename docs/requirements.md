# Map PreView requirements traceability

Sources supplied with the task:

- **R:** `PP Map PreView.pdf`, technical research report, 36 pages.
- **P:** `Plattform og modularkitektur.pdf`, platform and module architecture, 5 pages.

This increment is the shared base on which loaders and Minecraft versions will be built. **Implemented** means functioning base code. **Contract** means a typed boundary is implemented, while its game/mod adapter is still required. **Later** means an application or integration feature is intentionally outside this base. None of these labels asserts in-game compatibility.

| Requirement and source | Base location | Status and remaining work |
| --- | --- | --- |
| Runtime naming and English code descriptions; task instruction | `MapPreView`, configuration, manifests, all source files | Implemented; distribution branding remains separate |
| Shared core with loader/version separation; P1, R24-28 | Module dependency graph, `minecraft-common`, `platforms/common` | Implemented; Fabric 1.20.1/Yarn is the first planned adapter |
| Fabric, Forge, NeoForge and Quilt path; P1, R26-28 | `docs/porting.md`, `platforms/`, `versions/` | Contract; no unsupported loader JARs are produced |
| Actual active registries/generator, generic path, verified fast path; P1, R1-6, R12 | `WorldCreationSnapshot`, `MinecraftWorldgenBridge`, `BackendSelector` | Contract; native biome/height/column implementations require a version adapter |
| Dynamic dimensions and biome registry IDs; P1, R4-6 | `PreviewDimension`, `BiomePalette`, snapshot dimension enumeration | Implemented data model; native registry bootstrap is a contract |
| Separate sampling/renderer, tiled zoom pyramid; P1, R6-14 | `TileRequest`, `ViewportPlanner`, `PreviewCamera`, `PreviewRenderer` | Implemented CPU side; native renderer remains a contract |
| Coarse-first visible work, center priority, bounded prefetch; R6-8, R12-14 | `WorkPriority`, `ViewportPlanner` | Implemented |
| Async generation, cancellation, seed/config epochs; R7, R11 | `TileEngine`, `PreviewSession` | Implemented and regression tested |
| Lazy layers and no hidden expensive sampling; P1, R8-9, R15-16 | `DataLayer`, `BackendCapabilities`, `TileSampling` | Implemented dispatch and contracts; unavailable channels fail explicitly |
| Full cache identity including ordered packs/mods/config/Tectonic/Y; R9-10 | `WorldgenEnvironment`, `PreviewContext`, `TileKey` | Implemented; adapters supply actual content digests |
| Primitive buffers, bounded LRU and compression; P4, R10-11 | `RasterTile`, `TileCache`, `RasterTileCodec` | Implemented; persistent disk eviction is later |
| Worker/render-thread boundary and batched texture rendering; R10-11, R30 | `RenderUploadQueue`, `PreviewRenderer` | Implemented handoff; native texture creation/draw/eviction is a contract |
| Namespaced HEX colors and tag/environment/fallback colors; P1, R14-15 | `BiomeColors`, `PreviewConfig` | Implemented |
| Filters and recoloring without regenerating world data; P1, R14-15 | `PreviewFilter`, `TileColorizer` | Implemented; native filter controls are later |
| Height, topography, slope and land/ocean; P1, R8, R15 | Halo height channel and `TileColorizer` | Implemented CPU visualization; native height sampler is a contract |
| Candidate versus verified structures; P1, R6, R8 | `StructureQuery`, `PreviewStructure`, `StructureTile` | Implemented accuracy/limit contracts; native placement/verification is later |
| Cave biome, density and accurate block modes; P1, R15-16 | Independent channel/capability and sampler methods | Contract; accurate mode may use minimal native generation |
| Seed parsing/randomization and existing vanilla Create flow; P1, P3, R13, R16-17 | `WorldCreationInput`, `PreviewUiApi`, `applyToVanillaCreation` | Contract; native UI and version-specific seed parsing are later |
| Server pregeneration separated from preview; P2, R17-18 | `PregenController`, `PregenBridge` | Implemented controller; native server/ticket bridge is a contract |
| Rectangle, circle, free-form polygon, spiral; P2, P4 | `ChunkAreas`, `ChunkPlan` | Implemented streaming shapes and traversal; hardware-specific order tuning requires profiling |
| In-flight budget, C2ME/native scheduler cooperation; P2-4, R17-18 | `PregenSettings`, `PregenBridge` | Implemented budget; native scheduling integration is a contract |
| Progress, actual/average rate, totals, percent and ETA; P2 | `PregenProgress`, controller rate buckets | Implemented data; native screen/map overlay is later |
| Pause/cancel/background, world-join cancellation and resume; P2 | Server state machine and checksummed identity in `PregenCheckpoint` | Implemented service; confirmation/resume dialogs are later |
| Safe save barriers, retries and checkpoint persistence; P2, P3 | `PregenBridge.flush`, `PregenController.checkpoint`, `AtomicJsonStore` | Implemented base behavior; real chunk saving belongs to the bridge |
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
| Actual-world parity and named modpack matrix; R30-32 | `docs/compatibility.md`, adapter acceptance gates | Later, explicitly not replaced with synthetic fixtures |
| Reproducible performance measurements and no unsupported speed claims; P4-5, R32-33 | `benchmarks`, benchmark protocol | Synthetic base benchmark implemented; Minecraft/FPS/comparative metrics are later |
| Purpose, API, performance and integration documentation; P4-5 | Architecture, porting, schema, traceability and benchmark docs | Implemented for the base and its remaining boundaries |

The source PDFs contain illustrative and sometimes conflicting recommendations. The architecture document records the resulting choices instead of claiming that speculative APIs or experimental GPU paths are already implemented.
