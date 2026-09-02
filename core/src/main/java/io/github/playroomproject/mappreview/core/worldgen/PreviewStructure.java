package io.github.playroomproject.mappreview.core.worldgen;

import io.github.playroomproject.mappreview.core.api.ResourceId;
import java.util.Objects;

public record PreviewStructure(ResourceId id, int x, int y, int z, SupportLevel accuracy) {
    public PreviewStructure {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(accuracy, "accuracy");
        if (accuracy != SupportLevel.ESTIMATED && accuracy != SupportLevel.VERIFIED) {
            throw new IllegalArgumentException("Structures must be candidates or verified starts");
        }
    }
}
