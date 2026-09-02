package io.github.playroomproject.mappreview.minecraft;

import io.github.playroomproject.mappreview.core.api.PreviewContext;
import io.github.playroomproject.mappreview.core.api.ResourceId;
import io.github.playroomproject.mappreview.core.worldgen.BackendFactory;
import java.util.List;

/**
 * Version-owned registry lifetime. Dimensions are discovered from the bootstrapped world stems.
 * Close only after all samplers created from the snapshot have drained and released their leases.
 */
public interface WorldCreationSnapshot extends AutoCloseable {
    WorldCreationInput input();
    List<PreviewContext> dimensions();
    BackendFactory backend(ResourceId dimension);
    @Override void close();
}
