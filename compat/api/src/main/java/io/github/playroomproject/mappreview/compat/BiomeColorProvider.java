package io.github.playroomproject.mappreview.compat;

import io.github.playroomproject.mappreview.core.api.PreviewBiome;
import io.github.playroomproject.mappreview.core.api.PrioritizedProvider;

/** Return zero to defer to another provider. Explicit user overrides retain precedence. */
public interface BiomeColorProvider extends PrioritizedProvider {
    int color(PreviewBiome biome);
}
