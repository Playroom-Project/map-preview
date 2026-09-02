# Minecraft 1.20.1 adapter contract

Status: planned. Primary mapping namespace: Yarn. Java baseline: 17.

Implement detached world creation/registry snapshots, active `BiomeSource`/`ChunkGenerator` sampling, dimension enumeration, native rendering and server ticket/save integration. The native types remain here; shared modules retain their current mappings-free API.

Use the public API first, then Fabric events in the loader binding, narrow accessors/invokers and minimal mixins only where required. The first playable milestone is biome preview with seed changes, pan/zoom, dynamic dimensions, bounded cache and render-thread texture uploads.
