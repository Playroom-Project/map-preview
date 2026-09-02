package io.github.playroomproject.mappreview.core.api;

import java.util.Objects;

/** Dimensions come from the active world creation registry, including modded dimensions. */
public record PreviewDimension(ResourceId id, int minY, int maxYExclusive, int seaLevel) {
    public PreviewDimension {
        Objects.requireNonNull(id, "id");
        if (minY >= maxYExclusive) {
            throw new IllegalArgumentException("Dimension height range must be nonempty");
        }
    }

    public boolean containsY(int y) { return y >= minY && y < maxYExclusive; }
}
