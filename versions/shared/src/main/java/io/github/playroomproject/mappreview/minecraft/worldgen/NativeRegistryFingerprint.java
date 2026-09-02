package io.github.playroomproject.mappreview.minecraft.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import io.github.playroomproject.mappreview.core.cache.Fingerprint;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryLoader;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;

/** Hashes effective worldgen registry contents, including entries referenced indirectly by a generator. */
public final class NativeRegistryFingerprint {
    private NativeRegistryFingerprint() { }
    public static Fingerprint capture(RegistryWrapper.WrapperLookup registries) {
        var hash = Fingerprint.builder().add("native-worldgen-registry-v2");
        RegistryLoader.DYNAMIC_REGISTRIES.stream()
                .sorted(java.util.Comparator.comparing(entry -> entry.key().getValue().toString()))
                .forEach(entry -> addEntry(hash, registries, entry));
        return hash.finish();
    }
    private static <T> void addEntry(Fingerprint.Builder hash, RegistryWrapper.WrapperLookup registries,
                                    RegistryLoader.Entry<T> entry) {
        add(hash, registries, entry.key(), entry.elementCodec());
    }
    private static <T> void add(Fingerprint.Builder hash, RegistryWrapper.WrapperLookup registries,
                                 RegistryKey<? extends Registry<T>> key, Codec<T> codec) {
        var ops = RegistryOps.of(JsonOps.INSTANCE, registries);
        var entries = registries.getWrapperOrThrow(key).streamEntries()
                .sorted(java.util.Comparator.comparing(entry -> entry.registryKey().getValue().toString())).toList();
        hash.add(key.getValue().toString()).add(entries.size());
        for (var entry : entries) {
            hash.add(entry.registryKey().getValue().toString());
            var encoded = codec.encodeStart(ops, entry.value()).result();
            if (encoded.isEmpty()) { throw new IllegalArgumentException("Cannot fingerprint worldgen registry " + key.getValue()); }
            hash.add(encoded.get().toString());
            var tags = entry.streamTags().map(tag -> tag.id().toString()).sorted().toList();
            hash.add(tags.size());
            tags.forEach(hash::add);
        }
    }
}
