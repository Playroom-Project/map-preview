package io.github.playroomproject.mappreview.benchmark;

import io.github.playroomproject.mappreview.core.camera.PreviewCamera;
import io.github.playroomproject.mappreview.core.scheduler.*;
import io.github.playroomproject.mappreview.core.tile.DataLayer;
import io.github.playroomproject.mappreview.core.tile.TileData;
import io.github.playroomproject.mappreview.core.worldgen.HeightMode;
import io.github.playroomproject.mappreview.core.worldgen.StructureQuery;
import io.github.playroomproject.mappreview.testing.SyntheticWorldgen;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** End-to-end scheduler fixture. Results are not Minecraft, FPS or competitor measurements. */
public final class EngineBenchmark {
    private static final ViewportPlanner PLANNER = new ViewportPlanner(256, List.of(32, 16, 8, 4, 1), 1, 4096);
    private EngineBenchmark() { }

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        int iterations = 5;
        Path output = null;
        for (String argument : args) {
            if (argument.startsWith("--iterations=")) { iterations = Integer.parseInt(argument.substring(13)); }
            else if (argument.startsWith("--output=")) { output = Path.of(argument.substring(9)); }
            else { throw new IllegalArgumentException("Unknown argument: " + argument); }
        }
        if (iterations < 1 || iterations > 100) { throw new IllegalArgumentException("Iterations must be between 1 and 100"); }
        var lines = new ArrayList<String>();
        lines.add("fixture,java,processors,workers,iteration,scenario,metric,value,unit");
        for (int i = -2; i < iterations; i++) {
            List<Metric> metrics = runIteration();
            if (i >= 0) {
                for (Metric metric : metrics) {
                    lines.add(String.join(",", "synthetic-v1", csv(System.getProperty("java.runtime.version")),
                            Integer.toString(Runtime.getRuntime().availableProcessors()), "4", Integer.toString(i + 1),
                            metric.scenario(), metric.name(), Long.toString(metric.value()), metric.unit()));
                }
            }
        }
        if (output == null) { lines.forEach(System.out::println); }
        else {
            Path absolute = output.toAbsolutePath().normalize();
            if (absolute.getParent() != null) { Files.createDirectories(absolute.getParent()); }
            Files.write(absolute, lines, StandardCharsets.UTF_8);
        }
    }

    private static List<Metric> runIteration() throws Exception {
        var metrics = new ArrayList<Metric>();
        long coldStart = System.nanoTime();
        var engine = new TileEngine(new EngineLimits(4, 512, 64L * 1024 * 1024));
        try {
            var session = engine.beginSession(SyntheticWorldgen.context(123456789L), SyntheticWorldgen.factory(4));
            var camera = new PreviewCamera(0, 0, 4, 640, 480);
            ViewResult cold = generate(engine, session, camera);
            long startup = cold.completeAbsolute() - cold.complete() - coldStart;
            metrics.add(new Metric("cold_engine", "first_tile", startup + cold.firstTile(), "ns"));
            metrics.add(new Metric("cold_engine", "visible_coarse_25_percent", startup + cold.quarterCoarse(), "ns"));
            metrics.add(new Metric("cold_engine", "visible_coarse_complete", startup + cold.coarseComplete(), "ns"));
            metrics.add(new Metric("cold_engine", "requested_lod_complete", startup + cold.complete(), "ns"));
            long hits = engine.cache().stats().hits();
            long cachedStart = System.nanoTime();
            generate(engine, session, camera);
            metrics.add(new Metric("cached_view", "complete", System.nanoTime() - cachedStart, "ns"));
            metrics.add(new Metric("cached_view", "cache_hits", engine.cache().stats().hits() - hits, "tiles"));

            var dense = PLANNER.plan(camera, DataLayer.BIOMES, 64, HeightMode.WORLD_SURFACE, StructureQuery.DEFAULT)
                    .stream().filter(tile -> tile.priority().refinementPass() >= 3 && !tile.priority().prefetch()).limit(64).toList();
            for (var tile : dense) {
                var r = tile.request();
                var request = new io.github.playroomproject.mappreview.core.tile.TileRequest(r.tileX(), r.tileZ(), r.tileSize(), 1,
                        r.layer(), r.y(), r.heightMode(), r.structures());
                engine.request(session, request, tile.priority());
            }
            long switchStart = System.nanoTime();
            var replacement = engine.beginSession(SyntheticWorldgen.context(987654321L), SyntheticWorldgen.factory(4));
            ViewResult switched = generate(engine, replacement, camera);
            metrics.add(new Metric("seed_switch", "first_new_tile", switched.completeAbsolute() - switched.complete() + switched.firstTile() - switchStart, "ns"));
            metrics.add(new Metric("seed_switch", "cancelled_jobs", engine.stats().cancelled(), "jobs"));

            var zoomed = camera.zoomAt(camera.width() / 2.0, camera.height() / 2.0, 0.5);
            var wanted = PLANNER.plan(zoomed, DataLayer.BIOMES, 64, HeightMode.WORLD_SURFACE, StructureQuery.DEFAULT);
            engine.retainRequests(replacement, wanted.stream().map(tile -> replacement.key(tile.request())).collect(java.util.stream.Collectors.toSet()));
            long zoomHits = engine.cache().stats().hits();
            ViewResult zoom = generate(engine, replacement, zoomed);
            metrics.add(new Metric("zoom", "requested_lod_complete", zoom.complete(), "ns"));
            metrics.add(new Metric("zoom", "cache_hits", engine.cache().stats().hits() - zoomHits, "tiles"));
            long panHits = engine.cache().stats().hits();
            ViewResult panned = generate(engine, replacement, zoomed.pan(-128, 64));
            metrics.add(new Metric("pan", "first_tile", panned.firstTile(), "ns"));
            metrics.add(new Metric("pan", "requested_lod_complete", panned.complete(), "ns"));
            metrics.add(new Metric("pan", "cache_hits", engine.cache().stats().hits() - panHits, "tiles"));
            metrics.add(new Metric("memory", "accounted_cache_bytes", engine.cache().stats().bytes(), "bytes"));
        } finally {
            engine.close();
            if (!engine.awaitTermination(Duration.ofSeconds(10))) { throw new IllegalStateException("Benchmark workers did not stop"); }
        }
        return metrics;
    }

    private static ViewResult generate(TileEngine engine, PreviewSession session, PreviewCamera camera) throws Exception {
        List<ViewportPlanner.PlannedTile> plan = PLANNER.plan(camera, DataLayer.BIOMES, 64, HeightMode.WORLD_SURFACE, StructureQuery.DEFAULT);
        long start = System.nanoTime();
        AtomicLong first = new AtomicLong();
        AtomicLong quarter = new AtomicLong();
        AtomicLong coarse = new AtomicLong();
        AtomicInteger coarseDone = new AtomicInteger();
        long visibleCoarse = plan.stream().filter(tile -> !tile.priority().prefetch() && tile.priority().refinementPass() == 0).count();
        int quarterTarget = Math.max(1, (int) ((visibleCoarse + 3) / 4));
        var futures = new ArrayList<CompletableFuture<TileData>>();
        for (ViewportPlanner.PlannedTile tile : plan) {
            CompletionStage<TileData> stage = engine.request(session, tile.request(), tile.priority());
            var future = stage.thenApply(data -> {
                long elapsed = System.nanoTime() - start;
                first.compareAndSet(0, elapsed);
                if (!tile.priority().prefetch() && tile.priority().refinementPass() == 0) {
                    int done = coarseDone.incrementAndGet();
                    if (done == quarterTarget) { quarter.compareAndSet(0, elapsed); }
                    if (done == visibleCoarse) { coarse.compareAndSet(0, elapsed); }
                }
                return data;
            }).toCompletableFuture();
            futures.add(future);
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(30, TimeUnit.SECONDS);
        long end = System.nanoTime();
        return new ViewResult(first.get(), quarter.get(), coarse.get(), end - start, end);
    }

    private static String csv(String value) {
        if (!value.contains(",") && !value.contains("\"") && !value.contains("\n")) { return value; }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    private record Metric(String scenario, String name, long value, String unit) {
        private Metric { if (value < 0) { throw new IllegalArgumentException("Negative benchmark measurement: " + name); } }
    }
    private record ViewResult(long firstTile, long quarterCoarse, long coarseComplete, long complete, long completeAbsolute) { }
}
