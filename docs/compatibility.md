# Map PreView compatibility status

No Minecraft/loader or third-party mod combination has been tested in game in this base increment. The shared tests use synthetic fixtures and fake native server/render ports. The following table is an implementation and acceptance plan, not a supported-mod list.

| Target or family | Planned strategy | Current status |
| --- | --- | --- |
| Vanilla, Fabric 1.20.1/Yarn | Active biome/generator bridge, then a parity-tested noise fast path | Bridge contract; game adapter pending |
| Forge, NeoForge, Quilt | Separate loader bindings and supported version-specific source sets | Shared base ready; target builds pending |
| Tectonic, Terralith, Tectonic + Terralith | Active generator/registries; versioned Tectonic config preview when required | Contracts; native parity pending |
| Biomes O' Plenty + TerraBlender | Registry and biome-source sampling, no hardcoded biome list | Native parity pending |
| Nature's Spirit, Regions Unexplored, other TerraBlender mods | Generic registry/sampler path | Native parity pending |
| BetterEnd and BetterNether | Dynamic dimension contexts and capability checks | Native dimension tests pending |
| Wilder Wild, WWEO, WWOO | Generic path first; specialize only for an actual contract difference | Native parity pending |
| OHBWG, Streams, CounteredSlabs | Generic path and explicit capability degradation where needed | Version/API identification and native tests pending |
| Geophilic, Lithosphere | Active datapack/generator configuration fingerprinting | Native parity pending |
| C2ME | Native server scheduling plus a controlled in-flight budget | Server adapter pending; no global setting mutation |
| Lithium, ModernFix | Isolated preview state, no assumptions about internal optimizations | Modpack lifecycle tests pending |
| Nvidium | Tested native rendering/compute capability provider | No automatic enable/disable heuristic in the base |
| FancyMenu | Stable UI facade, standard widgets and a versioned optional bridge | Native screens, actions and blocklist tests pending |
| Chunky | Optional `PregenBridge` implementation | Public API adapter pending |
| Distant Horizons | Completed native chunk/LOD public API bridge | Adapter pending; preview tiles are not DH LOD files |
| FTB Chunks, Xaero's maps, JourneyMap | Completed-chunk listener and supported public APIs | Adapters pending; no private cache files written |
| Nonstandard generators, including dummy column/height implementations | Report capability-specific unavailable/estimated output | Base capability behavior tested; native fixture pending |
| Large mixed modpack | Same-seed/dimension/generated-world parity and lifecycle stress | Pending actual game runtime |

The regression suite must eventually compare preview biomes, raw heights, surfaces and structure accuracy with the corresponding real generator/world output. Use fixed seeds and coordinate sets including tile/chunk boundaries, negative coordinates and unusual dimension heights. Do not label a placement candidate as a verified structure or a raw height as a decorated surface.
