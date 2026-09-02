# Minecraft version adapters

No version adapter is implemented yet. The first target is `mc-1.20.1` using Yarn. Mapping-sensitive generator, registries, world-creation, structure and renderer code belongs in one version family and implements the stable contracts from `minecraft-common` and `client-common`.

Follow [the porting guide](../docs/porting.md). Add real target dependencies and Cloche configuration with the adapter implementation, not as empty loader artifacts.
