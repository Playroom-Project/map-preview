package io.github.playroomproject.mappreview.core.worldgen;

import java.util.Objects;

public record LayerSupport(SupportLevel level, String explanation) {
    public LayerSupport {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(explanation, "explanation");
        if (level == SupportLevel.UNSUPPORTED && explanation.isBlank()) {
            throw new IllegalArgumentException("Unsupported capabilities need a user-facing explanation");
        }
    }
    public boolean supported() { return level != SupportLevel.UNSUPPORTED; }
}
