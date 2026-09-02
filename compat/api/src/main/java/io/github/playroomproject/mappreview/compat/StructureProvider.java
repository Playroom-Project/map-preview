package io.github.playroomproject.mappreview.compat;

import io.github.playroomproject.mappreview.core.api.CancellationToken;
import io.github.playroomproject.mappreview.core.api.PreviewContext;
import io.github.playroomproject.mappreview.core.api.PrioritizedProvider;
import io.github.playroomproject.mappreview.core.tile.StructureTile;
import io.github.playroomproject.mappreview.core.tile.TileKey;

public interface StructureProvider extends PrioritizedProvider {
    boolean supports(PreviewContext context, boolean verificationRequired);
    StructureTile sample(PreviewContext context, TileKey key, CancellationToken token);
}
