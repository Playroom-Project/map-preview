package io.github.playroomproject.mappreview.core.color;

import io.github.playroomproject.mappreview.core.api.BiomePalette;
import io.github.playroomproject.mappreview.core.api.ResourceId;
import java.util.Map;

/** Compiles namespaced overrides into one primitive lookup table per display revision. */
public final class BiomeColors {
    private final int[] colors;

    public BiomeColors(BiomePalette palette, Map<ResourceId, Integer> individual, Map<ResourceId, Integer> tags) {
        colors = new int[palette.size()];
        var orderedTags = tags.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
        for (int id = 0; id < colors.length; id++) {
            var biome = palette.biome(id);
            Integer color = individual.get(biome.id());
            if (color == null) {
                for (var tag : orderedTags) {
                    if (biome.tags().contains(tag.getKey())) { color = tag.getValue(); break; }
                }
            }
            if (color == null && biome.environmentalTint() != 0) { color = biome.environmentalTint(); }
            colors[id] = color != null ? color | 0xff000000 : deterministic(biome.id());
        }
    }

    public int argb(int localBiomeId) { return colors[localBiomeId]; }
    public int size() { return colors.length; }

    public static int parseHex(String value) {
        if (value == null || !value.matches("#[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("Colors must use #RRGGBB notation");
        }
        return 0xff000000 | Integer.parseInt(value.substring(1), 16);
    }

    private static int deterministic(ResourceId id) {
        int hash = id.value().hashCode();
        hash ^= hash >>> 16;
        hash *= 0x7feb352d;
        hash ^= hash >>> 15;
        int red = 64 + (hash & 127);
        int green = 64 + ((hash >>> 8) & 127);
        int blue = 64 + ((hash >>> 16) & 127);
        return 0xff000000 | red << 16 | green << 8 | blue;
    }
}
