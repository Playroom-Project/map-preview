package io.github.playroomproject.mappreview.minecraft;

import io.github.playroomproject.mappreview.core.api.ResourceId;
import java.util.Map;
import java.util.Objects;

/** Detached editor state. Seed parsing follows the selected Minecraft version in its bridge. */
public record WorldCreationInput(String seedText, ResourceId preset, Map<ResourceId, String> previewConfigJson) {
    public WorldCreationInput {
        Objects.requireNonNull(seedText, "seedText");
        Objects.requireNonNull(preset, "preset");
        previewConfigJson = Map.copyOf(previewConfigJson);
    }
}
