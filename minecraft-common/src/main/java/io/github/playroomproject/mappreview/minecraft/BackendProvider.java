package io.github.playroomproject.mappreview.minecraft;

import io.github.playroomproject.mappreview.core.api.PreviewContext;
import io.github.playroomproject.mappreview.core.api.PrioritizedProvider;
import io.github.playroomproject.mappreview.core.worldgen.BackendFactory;

/** Fast adapters must demonstrate parity with the actual generator before taking precedence. */
public interface BackendProvider extends PrioritizedProvider {
    boolean supports(PreviewContext context);
    BackendFactory create(PreviewContext context);
}
