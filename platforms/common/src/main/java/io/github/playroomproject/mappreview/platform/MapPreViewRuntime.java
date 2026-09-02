package io.github.playroomproject.mappreview.platform;

import io.github.playroomproject.mappreview.compat.PreviewExtensions;
import io.github.playroomproject.mappreview.config.PreviewConfig;
import io.github.playroomproject.mappreview.core.scheduler.TileEngine;
import java.util.Objects;

/** Composition root for client adapters; dedicated-server pregeneration does not instantiate it. */
public final class MapPreViewRuntime implements AutoCloseable {
    private final TileEngine engine;
    private final PreviewExtensions extensions;

    public MapPreViewRuntime(LoaderPlatform platform, PreviewConfig config, PreviewExtensions extensions) {
        Objects.requireNonNull(platform, "platform");
        if (!platform.isClient()) { throw new IllegalStateException("The preview runtime requires a client environment"); }
        this.extensions = Objects.requireNonNull(extensions, "extensions");
        extensions.freeze();
        engine = new TileEngine(config.engineLimits(platform.hardware()));
    }
    public TileEngine engine() { return engine; }
    public PreviewExtensions extensions() { return extensions; }
    @Override public void close() { engine.close(); }
}
