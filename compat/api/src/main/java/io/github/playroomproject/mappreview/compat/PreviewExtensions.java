package io.github.playroomproject.mappreview.compat;

import io.github.playroomproject.mappreview.core.api.ProviderRegistry;
import io.github.playroomproject.mappreview.minecraft.BackendProvider;

/** Public registration surface. Optional integrations are loaded by the loader adapter only when present. */
public final class PreviewExtensions {
    private final ProviderRegistry<BackendProvider> backends = new ProviderRegistry<>();
    private final ProviderRegistry<BiomeColorProvider> colors = new ProviderRegistry<>();
    private final ProviderRegistry<StructureProvider> structures = new ProviderRegistry<>();
    private final ProviderRegistry<ConfigPreviewProvider> configs = new ProviderRegistry<>();
    private final ProviderRegistry<DimensionAdapter> dimensions = new ProviderRegistry<>();

    public void registerBackendProvider(BackendProvider provider) { backends.register(provider); }
    public void registerBiomeColorProvider(BiomeColorProvider provider) { colors.register(provider); }
    public void registerStructureProvider(StructureProvider provider) { structures.register(provider); }
    public void registerConfigPreviewProvider(ConfigPreviewProvider provider) { configs.register(provider); }
    public void registerDimensionAdapter(DimensionAdapter adapter) { dimensions.register(adapter); }
    public ProviderRegistry<BackendProvider> backends() { return backends; }
    public ProviderRegistry<BiomeColorProvider> colors() { return colors; }
    public ProviderRegistry<StructureProvider> structures() { return structures; }
    public ProviderRegistry<ConfigPreviewProvider> configs() { return configs; }
    public ProviderRegistry<DimensionAdapter> dimensions() { return dimensions; }
    public void freeze() { backends.freeze(); colors.freeze(); structures.freeze(); configs.freeze(); dimensions.freeze(); }
}
