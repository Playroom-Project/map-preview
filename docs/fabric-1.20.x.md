# Fabric 1.20.x

Every supported target has a separate artifact and exact Minecraft dependency. Do not use the 1.20.1 JAR on 1.20.6.

| Minecraft | Game Java | Fabric API | Yarn |
| --- | --- | --- | --- |
| 1.20 | 17 | 0.83.0+1.20 | 1.20+build.1 |
| 1.20.1 | 17 | 0.92.6+1.20.1 | 1.20.1+build.10 |
| 1.20.2 | 17 | 0.91.6+1.20.2 | 1.20.2+build.4 |
| 1.20.3 | 17 | 0.91.1+1.20.3 | 1.20.3+build.1 |
| 1.20.4 | 17 | 0.97.3+1.20.4 | 1.20.4+build.3 |
| 1.20.5 | 21 | 0.97.8+1.20.5 | 1.20.5+build.1 |
| 1.20.6 | 21 | 0.100.8+1.20.6 | 1.20.6+build.3 |

Install Fabric Loader 0.16.14 and put the matching Map PreView and Fabric API JARs in `mods/`. Only the main remapped JAR is installable. Source, dev, config-internal and foundation JARs are build artifacts.

## World preview

Open **Singleplayer → Create New World → Map PreView**. World type, datapacks and creation options remain in the vanilla editor. The preview captures its actual selected dimensions and serialized generators.

- Edit or randomize the seed. Old work is cancelled immediately; replacement work is debounced.
- Select a dimension, layer and Y slice. Unavailable native operations are skipped.
- Drag to pan; scroll to zoom; Reset map returns to the origin.
- Type a biome ID to filter the display, or set a `#RRGGBB` override for that biome.
- Use seed returns the selected seed to the vanilla editor. Create world follows vanilla's creation path.

Biomes and cave biomes are native quart samples at the indicated Y. Height, slope, topography and land/ocean use raw generator heights. Raw surface colors come from the generator's unmodified columns. Density is an estimate without carvers, aquifers and placed features. Slime uses Minecraft's native seed rule. Yellow structure markers are possible placements, not verified starts; they respect the world's Generate Structures option.

Preview sampling never generates or saves real chunks. Generator clones and noise state belong to individual workers. Unknown generator/biome-source implementations run serially. The CPU cache, outstanding work, coloring queue and upload queue are bounded; the GPU atlas occupies 16 MiB.

## Pregeneration

In a singleplayer world, open the pause menu and select **Map PreView: Pregenerate**. Choose dimension, square/circle/polygon, traversal and maximum chunks in flight. Start, pause, cancel and explicit resume run on the integrated server. The control panel keeps it ticking while open. Closing the panel preserves the job; a normally paused integrated server pauses its ticks.

Operators can also use:

```text
/map_preview status
/map_preview start square 256
/map_preview start circle 512
/map_preview polygon 0,0;256,0;256,256;0,256
/map_preview pause
/map_preview cancel
/map_preview resume
```

The command source supplies center and dimension. Radius and polygon inputs use blocks. Circles round outward to a chunk radius; polygons snap vertices to chunk-grid edges. Both already existing FULL chunks and newly generated chunks count as processed. The adapter cannot reliably distinguish those two cases and does not invent a newly-generated count.

Pause and cancel stop new submissions, drain existing native work and save a checkpoint after a native save barrier. Checkpoints live in `<world>/map_preview/pregen.json`; `world-id.json` distinguishes different saves with identical seeds. Resume is explicit and rejects an incompatible identity. The service periodically pauses admission for a safe checkpoint. An interrupted shutdown may repeat work after the last safe checkpoint. Sparse traversal yields after a bounded scan budget on each tick; oversized polygon planning requests are rejected with an instruction to split the area.

Configuration is in `config/map_preview.json`. Biome color changes use validated atomic writes with a backup. Bad configuration files are preserved and reported, with defaults used for that session.

## Development launches

```sh
./gradlew -PminecraftVersion=1.20.1 :fabric:runClient
./gradlew -PminecraftVersion=1.20.1 :fabric:runServer
```

Run directories are separate per Minecraft version. Native servers retain Minecraft's normal EULA/world setup. See [validation](validation-1.20.x.md) for completed checks and [compatibility](compatibility.md) for untested integrations.

Build and verify the complete matrix with both JDK 17 and JDK 21 installed, using JDK 21 to launch Gradle:

```sh
python3 scripts/build_fabric_matrix.py --game-tests
```

For one target, run `./gradlew -PminecraftVersion=1.20.1 -PgameTests check assemble :fabric:runGameTest`, then `python3 scripts/verify_fabric_jar.py 1.20.1`. Native GameTests use Fabric's test mode and their own per-version run directories. The test-only entrypoint is excluded from the distributed mod.
