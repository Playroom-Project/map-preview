package io.github.playroomproject.mappreview.core.api;

import io.github.playroomproject.mappreview.core.cache.Fingerprint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Converts registry identities to stable session-local integers once, outside sampling loops. */
public final class BiomePalette {
    private final List<PreviewBiome> biomes;
    private final Map<ResourceId, Integer> indices;
    private final Fingerprint fingerprint;

    public BiomePalette(List<PreviewBiome> entries) {
        if (entries.isEmpty()) { throw new IllegalArgumentException("A biome palette cannot be empty"); }
        var sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparing(PreviewBiome::id));
        var ids = new HashMap<ResourceId, Integer>();
        var hash = Fingerprint.builder().add("biome-palette-v1").add(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            PreviewBiome biome = sorted.get(i);
            if (ids.put(biome.id(), i) != null) {
                throw new IllegalArgumentException("Duplicate biome: " + biome.id());
            }
            hash.add(biome.id().value()).add(biome.environmentalTint());
            var tags = biome.tags().stream().sorted().toList();
            hash.add(tags.size());
            tags.forEach(tag -> hash.add(tag.value()));
        }
        biomes = List.copyOf(sorted);
        indices = Map.copyOf(ids);
        fingerprint = hash.finish();
    }

    public int size() { return biomes.size(); }
    public PreviewBiome biome(int localId) { return biomes.get(localId); }
    public List<PreviewBiome> entries() { return biomes; }
    public Fingerprint fingerprint() { return fingerprint; }

    public int localId(ResourceId id) {
        Integer result = indices.get(id);
        if (result == null) { throw new IllegalArgumentException("Unknown biome: " + id); }
        return result;
    }
}
