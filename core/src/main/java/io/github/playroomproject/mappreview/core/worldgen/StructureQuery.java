package io.github.playroomproject.mappreview.core.worldgen;

import io.github.playroomproject.mappreview.core.api.ResourceId;
import java.util.Set;

/** An empty ID set requests all registered structures. Limits are part of the cache key. */
public record StructureQuery(Set<ResourceId> ids, int maximumResults) {
    public static final StructureQuery DEFAULT = new StructureQuery(Set.of(), 4096);

    public StructureQuery {
        ids = Set.copyOf(ids);
        if (maximumResults < 1 || maximumResults > 65_536 || ids.size() > 4096) {
            throw new IllegalArgumentException("Structure result limit must be between 1 and 65536");
        }
    }
}
