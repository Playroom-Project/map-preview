package io.github.playroomproject.mappreview.core.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Registration happens at bootstrap; immutable snapshots are safe for worker consumption. */
public final class ProviderRegistry<T extends PrioritizedProvider> {
    private volatile List<T> providers = List.of();
    private boolean frozen;

    public synchronized void register(T provider) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(provider.id(), "provider ID");
        if (frozen) { throw new IllegalStateException("Map PreView provider registry is frozen"); }
        if (providers.stream().anyMatch(existing -> existing.id().equals(provider.id()))) {
            throw new IllegalArgumentException("Duplicate Map PreView provider: " + provider.id());
        }
        var next = new ArrayList<>(providers);
        next.add(provider);
        next.sort(Comparator.<T>comparingInt(PrioritizedProvider::priority).reversed().thenComparing(PrioritizedProvider::id));
        providers = List.copyOf(next);
    }

    public List<T> providers() { return providers; }
    public synchronized void freeze() { frozen = true; }
}
