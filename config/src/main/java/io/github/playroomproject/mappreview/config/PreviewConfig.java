package io.github.playroomproject.mappreview.config;

import io.github.playroomproject.mappreview.core.MapPreView;
import io.github.playroomproject.mappreview.core.api.ResourceId;
import io.github.playroomproject.mappreview.core.color.BiomeColors;
import io.github.playroomproject.mappreview.core.scheduler.EngineLimits;
import io.github.playroomproject.mappreview.core.scheduler.HardwareProfile;
import io.github.playroomproject.mappreview.core.scheduler.PerformanceMode;
import io.github.playroomproject.mappreview.core.scheduler.ViewportPlanner;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Schema descriptions live in docs/config.schema.json because standard JSON has no comments. */
public record PreviewConfig(int schemaVersion, String name, PerformanceMode performanceMode, int workers,
                            int maximumOutstandingTasks, long cacheMiB, int tileSize, List<Integer> lodSteps,
                            int prefetchRings, int maximumViewportTiles, int uploadsPerFrame, long uploadQueueMiB,
                            Map<String, String> biomeColors, Map<String, String> biomeTagColors,
                            Set<String> disabledIntegrations, HardwareProfile hardware) {
    public static final int SCHEMA_VERSION = 1;

    public PreviewConfig {
        Objects.requireNonNull(performanceMode, "performanceMode");
        Objects.requireNonNull(hardware, "hardware");
        lodSteps = List.copyOf(lodSteps);
        biomeColors = Map.copyOf(biomeColors);
        biomeTagColors = Map.copyOf(biomeTagColors);
        disabledIntegrations = Set.copyOf(disabledIntegrations);
        if (schemaVersion != SCHEMA_VERSION || !MapPreView.NAME.equals(name) || workers < 0 || workers > 64
                || maximumOutstandingTasks < Math.max(64, workers) || maximumOutstandingTasks > 16_384
                || cacheMiB < 0 || cacheMiB > 32_768 || uploadsPerFrame < 1 || uploadsPerFrame > 256
                || uploadQueueMiB < 1 || uploadQueueMiB > 1024 || biomeColors.size() > 65_536 || biomeTagColors.size() > 4096) {
            throw new IllegalArgumentException("Invalid or unsupported Map PreView configuration");
        }
        new ViewportPlanner(tileSize, lodSteps, prefetchRings, maximumViewportTiles);
        validateColors(biomeColors);
        validateColors(biomeTagColors);
        disabledIntegrations.forEach(ResourceId::new);
    }

    public static PreviewConfig defaults(HardwareProfile hardware) {
        return new PreviewConfig(SCHEMA_VERSION, MapPreView.NAME, PerformanceMode.BALANCED, 0,
                256, 256, 256, List.of(32, 16, 8, 4, 1), 1, 4096, 8, 16, Map.of(), Map.of(), Set.of(), hardware);
    }

    public EngineLimits engineLimits(HardwareProfile currentHardware) {
        int count = workers == 0 ? performanceMode.workers(currentHardware) : workers;
        long budget = Math.min(Math.multiplyExact(cacheMiB, 1_048_576L), currentHardware.maximumHeapBytes() / 4);
        return new EngineLimits(count, maximumOutstandingTasks, budget);
    }

    private static void validateColors(Map<String, String> colors) {
        colors.forEach((key, value) -> { new ResourceId(key); BiomeColors.parseHex(value); });
    }
}
