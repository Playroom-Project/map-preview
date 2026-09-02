package io.github.playroomproject.mappreview.compat;

import io.github.playroomproject.mappreview.core.api.PreviewContext;
import io.github.playroomproject.mappreview.core.api.PrioritizedProvider;
import io.github.playroomproject.mappreview.core.worldgen.BackendFactory;

public interface DimensionAdapter extends PrioritizedProvider {
    boolean supports(PreviewContext context);
    BackendFactory create(PreviewContext context);
}
