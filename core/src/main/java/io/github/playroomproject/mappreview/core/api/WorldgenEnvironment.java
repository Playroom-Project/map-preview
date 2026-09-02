package io.github.playroomproject.mappreview.core.api;

import io.github.playroomproject.mappreview.core.cache.Fingerprint;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Content identities must describe the actual bootstrapped runtime, not filenames or timestamps.
 * Datapack order is significant; mod and configuration map order is not.
 */
public record WorldgenEnvironment(
        String minecraftVersion, String loaderId, String loaderVersion,
        ResourceId worldPreset, String generatorIdentity, Fingerprint generatorSettings,
        List<String> orderedDatapackDigests, Map<String, String> worldgenMods,
        Map<String, String> worldgenConfigDigests) {
    public WorldgenEnvironment {
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        Objects.requireNonNull(loaderId, "loaderId");
        Objects.requireNonNull(loaderVersion, "loaderVersion");
        Objects.requireNonNull(worldPreset, "worldPreset");
        Objects.requireNonNull(generatorIdentity, "generatorIdentity");
        Objects.requireNonNull(generatorSettings, "generatorSettings");
        orderedDatapackDigests = List.copyOf(orderedDatapackDigests);
        worldgenMods = Map.copyOf(worldgenMods);
        worldgenConfigDigests = Map.copyOf(worldgenConfigDigests);
        if (minecraftVersion.isBlank() || loaderId.isBlank() || loaderVersion.isBlank() || generatorIdentity.isBlank()) {
            throw new IllegalArgumentException("World generation identity fields cannot be blank");
        }
    }

    public Fingerprint fingerprint() {
        var hash = Fingerprint.builder().add("environment-v1").add(minecraftVersion)
                .add(loaderId).add(loaderVersion).add(worldPreset.value())
                .add(generatorIdentity).add(generatorSettings.hex()).add(orderedDatapackDigests.size());
        orderedDatapackDigests.forEach(hash::add);
        return hash.addSorted(worldgenMods).addSorted(worldgenConfigDigests).finish();
    }
}
