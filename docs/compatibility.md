# Map PreView compatibility status

Fabric has a native implementation with passing generator tests, server GameTests and packaged graphical client checks on all seven targets. The 1.20.6 client also created a world from the preview and completed integrated-server pregeneration. Exact results and reproduction steps are recorded in [validation](validation-1.20.x.md). Third-party modpack compatibility remains untested.

| Target or family | Planned strategy | Current status |
| --- | --- | --- |
| Vanilla, Fabric 1.20–1.20.6/Yarn | Active registries, worker-owned native generators and bounded native server scheduling | All seven native builds, server GameTests and packaged graphical client checks passed |
| Forge, NeoForge, Quilt | Separate loader bindings and supported version-specific source sets | Resolved port descriptors prepared; native loader ports and artifacts pending |
| Tectonic, Terralith, Tectonic + Terralith | Active generator/registries; versioned Tectonic config preview when required | Contracts; native parity pending |
| Biomes O' Plenty + TerraBlender | Registry and biome-source sampling, no hardcoded biome list | Native parity pending |
| Nature's Spirit, Regions Unexplored, other TerraBlender mods | Generic registry/sampler path | Native parity pending |
| BetterEnd and BetterNether | Dynamic dimension contexts and capability checks | Vanilla/custom-dimension enumeration tested; these mods remain untested |
| Wilder Wild, WWEO, WWOO | Generic path first; specialize only for an actual contract difference | Native parity pending |
| OHBWG, Streams, CounteredSlabs | Generic path and explicit capability degradation where needed | Version/API identification and native tests pending |
| Geophilic, Lithosphere | Active datapack/generator configuration fingerprinting | Native parity pending |
| C2ME | Native server scheduling plus a controlled in-flight budget | Vanilla server adapter tested; C2ME integration untested; no global setting mutation |
| Lithium, ModernFix | Isolated preview state, no assumptions about internal optimizations | Modpack lifecycle tests pending |
| Nvidium | Explicit rendering capability detection | Native atlas renderer implemented; Nvidium capability bridge and interaction tests pending |
| FancyMenu | Stable UI facade, standard widgets and a versioned optional bridge | Native preview and pregeneration screens implemented; optional bridge and blocklist tests pending |
| Chunky | Optional `PregenBridge` implementation | Public API adapter pending |
| Distant Horizons | Completed native chunk/LOD public API bridge | Adapter pending; preview tiles are not DH LOD files |
| FTB Chunks, Xaero's maps, JourneyMap | Completed-chunk listener and supported public APIs | Adapters pending; no private cache files written |
| Nonstandard generators, including dummy column/height implementations | Report capability-specific unavailable/estimated output | Base capability behavior tested; native fixture pending |
| Large mixed modpack | Same-seed/dimension/generated-world parity and lifecycle stress | Pending actual game runtime |

Native tests compare preview biome and raw-height samples with the selected vanilla generator over fixed seeds, dimensions and negative coordinates. They also check independent workers, flat worlds, extra dimensions, slime rules, structure candidates and the world-creation invoker. Decorated surfaces, generated structure parity, third-party generators and hardware-specific rendering combinations remain unverified. Placement candidates are explicitly estimated; raw heights are not decorated surfaces.

Native identities include serialized dynamic registries and generators, enabled datapack order, mod versions and a separate editor-session identity. Pregeneration also includes the save's persistent UUID. Arbitrary mod configuration files and runtime state outside native codecs are not automatically tracked; those integrations need dedicated snapshot/fingerprint providers and acceptance tests before support can be claimed.
