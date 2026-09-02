package io.github.playroomproject.mappreview.minecraft.client;

import io.github.playroomproject.mappreview.config.AtomicJsonStore;
import io.github.playroomproject.mappreview.config.PreviewConfig;
import io.github.playroomproject.mappreview.core.MapPreView;
import io.github.playroomproject.mappreview.core.scheduler.HardwareProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Loader-independent settings storage. Invalid user files are preserved for diagnosis. */
public final class PreviewSettings {
    private final Path path;
    private final AtomicJsonStore store = new AtomicJsonStore();
    private final java.util.concurrent.ExecutorService writer = new java.util.concurrent.ThreadPoolExecutor(1, 1, 0,
            java.util.concurrent.TimeUnit.MILLISECONDS, new java.util.concurrent.ArrayBlockingQueue<>(16), task -> {
                Thread thread = new Thread(task, "Map PreView settings"); thread.setDaemon(true); return thread;
            });
    private volatile PreviewConfig value = PreviewConfig.defaults(HardwareProfile.detectJvm());

    public PreviewSettings(Path configDirectory) { path = configDirectory.resolve("map_preview.json"); }
    public PreviewConfig value() { return value; }
    public void load() {
        if (!Files.isRegularFile(path)) { return; }
        try { value = store.read(path, PreviewConfig.class); }
        catch (IOException | RuntimeException exception) {
            MapPreView.LOGGER.log(System.Logger.Level.WARNING, "Map PreView could not read settings; using defaults", exception);
        }
    }
    public synchronized java.util.concurrent.CompletableFuture<Void> colors(Map<String, String> colors) {
        var previous = value;
        var next = new PreviewConfig(previous.schemaVersion(), previous.name(), previous.performanceMode(), previous.workers(),
                previous.maximumOutstandingTasks(), previous.cacheMiB(), previous.tileSize(), previous.lodSteps(),
                previous.prefetchRings(), previous.maximumViewportTiles(), previous.uploadsPerFrame(), previous.uploadQueueMiB(),
                colors, previous.biomeTagColors(), previous.disabledIntegrations(), previous.hardware());
        value = next;
        try {
            return java.util.concurrent.CompletableFuture.runAsync(() -> {
                try { store.write(path, next, true); }
                catch (IOException exception) { throw new java.util.concurrent.CompletionException(exception); }
            }, writer);
        } catch (java.util.concurrent.RejectedExecutionException exception) {
            return java.util.concurrent.CompletableFuture.failedFuture(exception);
        }
    }
    public void close() { writer.shutdown(); }
}
