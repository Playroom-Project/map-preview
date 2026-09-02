package io.github.playroomproject.mappreview.core.api;

import java.util.Objects;
import java.util.Set;

/** An optional environmental tint is opaque ARGB; zero means no tint was supplied. */
public record PreviewBiome(ResourceId id, Set<ResourceId> tags, int environmentalTint) {
    public PreviewBiome {
        Objects.requireNonNull(id, "id");
        tags = Set.copyOf(tags);
        if (environmentalTint != 0 && (environmentalTint >>> 24) != 255) {
            throw new IllegalArgumentException("Biome tints must be opaque ARGB");
        }
    }
}
