package io.github.playroomproject.mappreview.minecraft;

import io.github.playroomproject.mappreview.core.api.PreviewContext;
import io.github.playroomproject.mappreview.core.api.ProviderRegistry;
import io.github.playroomproject.mappreview.core.worldgen.BackendFactory;
import java.util.Objects;

/** Specialized providers opt in explicitly; the generic active-generator bridge remains the fallback. */
public final class BackendSelector {
    private final ProviderRegistry<BackendProvider> providers;
    public BackendSelector(ProviderRegistry<BackendProvider> providers) { this.providers = Objects.requireNonNull(providers, "providers"); }

    public BackendFactory select(PreviewContext context, BackendFactory genericFallback) {
        for (BackendProvider provider : providers.providers()) {
            if (provider.supports(context)) { return Objects.requireNonNull(provider.create(context), "provider backend"); }
        }
        return Objects.requireNonNull(genericFallback, "genericFallback");
    }
}
