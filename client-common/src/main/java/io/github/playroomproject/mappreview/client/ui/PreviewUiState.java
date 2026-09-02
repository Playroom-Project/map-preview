package io.github.playroomproject.mappreview.client.ui;

import io.github.playroomproject.mappreview.core.api.ResourceId;
import java.util.Objects;
import java.util.Optional;

/** Low-frequency UI snapshot; hot sampling and rendering paths use primitive fields instead. */
public record PreviewUiState(long seed, ResourceId dimension, Optional<ResourceId> hoveredBiome,
                             int hoveredX, int hoveredZ, double progress, String status) {
    public PreviewUiState {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(hoveredBiome, "hoveredBiome");
        Objects.requireNonNull(status, "status");
        if (!Double.isFinite(progress) || progress < 0 || progress > 1) { throw new IllegalArgumentException("Invalid preview progress"); }
    }
}
