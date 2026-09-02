package io.github.playroomproject.mappreview.core.scheduler;

import io.github.playroomproject.mappreview.core.cache.Fingerprint;
import java.util.Objects;

/** GPU strings are supplied by a render-thread platform probe, never by a core OpenGL call. */
public record HardwareProfile(int logicalProcessors, long maximumHeapBytes, String gpuVendor, String gpuRenderer) {
    public HardwareProfile {
        Objects.requireNonNull(gpuVendor, "gpuVendor");
        Objects.requireNonNull(gpuRenderer, "gpuRenderer");
        if (logicalProcessors < 1 || maximumHeapBytes < 1 || gpuVendor.length() > 512 || gpuRenderer.length() > 512) {
            throw new IllegalArgumentException("Invalid hardware profile");
        }
    }
    public static HardwareProfile detectJvm() {
        return new HardwareProfile(Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory(), "unknown", "unknown");
    }
    public Fingerprint fingerprint() {
        return Fingerprint.builder().add("hardware-v1").add(logicalProcessors).add(maximumHeapBytes).add(gpuVendor).add(gpuRenderer).finish();
    }
}
