package io.github.playroomproject.mappreview.minecraft;

import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.VanillaDataPackProvider;

/** Vanilla resource bootstrap for the legacy native test fixture. */
final class NativeTestResources {
    private NativeTestResources() { }
    static ResourcePackManager packs() { return new ResourcePackManager(new VanillaDataPackProvider()); }
    static DynamicRegistryManager.Immutable load(ResourceManager resources, DynamicRegistryManager base) {
        return RegistryLoader.load(resources, base, RegistryLoader.DYNAMIC_REGISTRIES);
    }
}
