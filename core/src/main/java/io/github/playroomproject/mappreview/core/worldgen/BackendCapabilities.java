package io.github.playroomproject.mappreview.core.worldgen;

import io.github.playroomproject.mappreview.core.tile.DataLayer;
import java.util.Map;

/** Missing capabilities degrade individually, never by pretending to return empty valid data. */
public final class BackendCapabilities {
    private static final LayerSupport UNAVAILABLE = new LayerSupport(
            SupportLevel.UNSUPPORTED, "This generator does not expose this preview operation");
    private final Map<DataLayer, LayerSupport> support;

    public BackendCapabilities(Map<DataLayer, LayerSupport> support) { this.support = Map.copyOf(support); }
    public LayerSupport support(DataLayer layer) { return support.getOrDefault(layer, UNAVAILABLE); }
    public boolean supports(DataLayer layer) { return support(layer).supported(); }
    public boolean roughTerrainAvailable() { return supports(DataLayer.HEIGHT); }
}
