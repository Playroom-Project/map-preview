# Minecraft version adapters

`shared` implements native Yarn worldgen, registry snapshots, screens, rendering and pregeneration. `legacy` covers 1.20/1.20.1; `modern` covers 1.20.2–1.20.4; `components` covers 1.20.5/1.20.6. Only actual API differences belong in those family directories. `gradle/targets.json` selects the family for each exact game build.

Follow [the porting guide](../docs/porting.md) and [validation report](../docs/validation-1.20.x.md). Shared Java algorithms remain in the game-independent modules; Forge/NeoForge official-mapping adapters must preserve their contracts.
