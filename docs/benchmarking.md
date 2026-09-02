# Map PreView validation and benchmark protocol

## Shared-base validation

```sh
./gradlew clean check assemble
python3 scripts/check_architecture.py
```

The JUnit suite checks cache identity, datapack order, local palette stability, negative coordinates, tile ownership, compressed-data corruption, bounded LRU admission, lazy sampling, capability accuracy, priorities, deduplication, cancellation, sampler thread ownership and shutdown.

Client tests cover recoloring without worldgen, missing layer dependencies, stale epochs/display revisions, upload limits, render-thread enforcement, mesh extent/winding and shared edges. Server tests cover exact shape enumeration, every cursor prefix, pause/cancel/drain, save barriers, failed retries, checkpoint validation, resumption and native ticket ownership. Configuration tests exercise strict JSON, duplicate/unknown fields, encoding and atomic backups.

These tests are meaningful foundation checks, not Minecraft GameTests. The production core JAR is separately checked for accidental inclusion of the synthetic fixture. CI is configured to run the Java 17-compatible source on JDK 17 and JDK 21.

## Synthetic engine measurements

```sh
./gradlew :benchmarks:run --args="--iterations=5 --output=results/synthetic.csv"
```

The output is `benchmarks/results/synthetic.csv`. Two warmup iterations precede the recorded iterations. Each iteration creates a fresh four-worker engine with a 64 MiB CPU cache and 512 outstanding-task limit. The fixture uses a 640 by 480 viewport, 4 blocks per pixel, 256-block base tiles, sample steps 32/16/8/4/1 and one coarse prefetch ring.

| Scenario | Measurement |
| --- | --- |
| Fresh engine in a warmed JVM | First tile, 25% of visible coarse tiles, all visible coarse tiles, requested LOD completion |
| Cached view | Completion time and cache hits |
| Seed replacement | First tile from the new seed and number of cancelled old jobs |
| Zoom | Requested LOD completion and cache reuse |
| Pan | First tile, requested LOD completion and cache reuse |
| Memory | Accounted CPU cache bytes after the scenario |

Cold-engine timings include engine construction and synthetic context setup. Seed-switch timing starts before the replacement context is created. The 25% figure counts visible coarse tiles, not exact screen pixel coverage. Cancellation counts are job counts, not a measurement of CPU time saved. Cache byte accounting is not total JVM heap use.

This is an end-to-end regression harness, not a statistically rigorous microbenchmark. Results vary with host contention, JIT compilation and GC. No synthetic number represents Minecraft generation throughput, native rendering FPS, GPU performance or a speedup over another mod. The manual GitHub benchmark workflow preserves raw CSV artifacts without enforcing a flaky performance threshold.

## Native acceptance protocol for the first adapter

Record the exact commit, Java runtime/flags, CPU, memory, GPU/driver, Minecraft version, loader version, mod versions/content, ordered datapacks, world preset/configuration, seed, dimension, viewport and information/accuracy level.

Use fixed coordinates spanning positive/negative values, tile/chunk boundaries, caves, varied terrain and all available dimensions. Compare preview output with the corresponding actual generator operation or generated world output. Treat raw height, decorated surface, structure placement candidates and verified starts as different quantities.

Measure these missing native metrics before any public performance claim:

- Actual cold game/session startup and first meaningful biome preview.
- Seed/config changes, first new tile, coarse and detailed completion.
- Pan latency, wasted cancelled work, render FPS and CPU utilization under load.
- Continuous zoom, time to the appropriate LOD and cache reuse ratio.
- Heap after one minute, prolonged panning, allocated bytes per sample and separate CPU/GPU budgets.
- Biome/height/column sampling throughput and structure regions per second.
- Actual server chunks per second, save/flush time, retries and checkpoint recovery with and without C2ME or Chunky.
- Native 3D/GPU costs and parity if an experimental compute backend is introduced.

For comparisons with World Preview, Genesis or another viewer, use the same machine, Java, Minecraft, modpack, seed, viewport, area and information/accuracy level. Publish raw measurements and variance. Do not extrapolate speed claims from this repository's synthetic backend.
