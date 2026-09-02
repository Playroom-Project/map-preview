# Fabric binding

Status: planned. First target: Minecraft 1.20.1 with Yarn and Fabric API hooks.

Implement `LoaderPlatform`, bootstrap optional providers, attach the version bridge to vanilla Create World, and register client/render/server lifecycle hooks. Cloche target assembly must package the shared libraries and generate `fabric.mod.json` with machine ID `map_preview` and the requested distribution title. Runtime diagnostics use `Map PreView`.
