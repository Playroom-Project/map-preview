package io.github.playroomproject.mappreview.minecraft.worldgen;

import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.dimension.DimensionOptions;

/** Copies either native dimension representation used in the 1.20 version family. */
public final class NativeDimensions {
    private NativeDimensions() { }

    public static Map<RegistryKey<DimensionOptions>, DimensionOptions> copyOf(Registry<DimensionOptions> dimensions) {
        return dimensions.getEntrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public static Map<RegistryKey<DimensionOptions>, DimensionOptions> copyOf(Map<RegistryKey<DimensionOptions>, DimensionOptions> dimensions) {
        return Map.copyOf(dimensions);
    }
}
