package io.github.playroomproject.mappreview.core.tile;

import io.github.playroomproject.mappreview.core.worldgen.PreviewStructure;
import io.github.playroomproject.mappreview.core.worldgen.SupportLevel;
import java.util.List;
import java.util.Objects;

/** Truncation is explicit; an incomplete query must never be presented as a complete structure set. */
public record StructureTile(TileKey key, List<PreviewStructure> structures, boolean truncated) implements TileData {
    public StructureTile {
        Objects.requireNonNull(key, "key");
        structures = List.copyOf(structures);
        if (!key.request().layer().structures() || structures.size() > key.request().structures().maximumResults()) {
            throw new IllegalArgumentException("Invalid structure tile");
        }
        if (key.request().layer() == DataLayer.VERIFIED_STRUCTURES
                && structures.stream().anyMatch(s -> s.accuracy() != SupportLevel.VERIFIED)) {
            throw new IllegalArgumentException("An unverified structure cannot enter the verified channel");
        }
    }

    @Override public long byteSize() {
        long bytes = key.byteSize() + 128;
        for (PreviewStructure structure : structures) { bytes += 128L + 2L * structure.id().value().length(); }
        return bytes;
    }
}
