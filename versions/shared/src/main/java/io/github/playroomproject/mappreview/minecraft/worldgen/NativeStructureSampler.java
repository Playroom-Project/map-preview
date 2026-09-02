package io.github.playroomproject.mappreview.minecraft.worldgen;

import io.github.playroomproject.mappreview.core.api.CancellationToken;
import io.github.playroomproject.mappreview.core.api.ResourceId;
import io.github.playroomproject.mappreview.core.tile.StructureTile;
import io.github.playroomproject.mappreview.core.tile.TileKey;
import io.github.playroomproject.mappreview.core.worldgen.PreviewStructure;
import io.github.playroomproject.mappreview.core.worldgen.SupportLevel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.gen.chunk.placement.StructurePlacement;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.structure.StructureSet;

/** Uses native spacing, exclusion and frequency rules; suitability is explicitly left unverified. */
final class NativeStructureSampler {
    private final StructurePlacementCalculator calculator;
    private final long seed;

    NativeStructureSampler(RegistryWrapper.WrapperLookup registries, ChunkGenerator generator, NoiseConfig noise, long seed) {
        this.seed = seed;
        calculator = StructurePlacementCalculator.create(noise, seed, generator.getBiomeSource(),
                registries.getWrapperOrThrow(RegistryKeys.STRUCTURE_SET));
    }

    StructureTile sample(TileKey key, CancellationToken token) {
        var request = key.request();
        int minX = Math.floorDiv(request.originX(), 16);
        int minZ = Math.floorDiv(request.originZ(), 16);
        int maxX = Math.floorDiv(request.originX() + request.tileSize() - 1, 16);
        int maxZ = Math.floorDiv(request.originZ() + request.tileSize() - 1, 16);
        var results = new ArrayList<PreviewStructure>();
        int remaining = 65_536;
        for (var entry : calculator.getStructureSets()) {
            token.check();
            var set = entry.value();
            var placement = set.placement();
            if (placement instanceof RandomSpreadStructurePlacement spread) {
                int spacing = spread.getSpacing();
                for (int z = Math.floorDiv(minZ, spacing); z <= Math.floorDiv(maxZ, spacing); z++) {
                    for (int x = Math.floorDiv(minX, spacing); x <= Math.floorDiv(maxX, spacing); x++) {
                        token.check();
                        if (remaining-- <= 0) { return new StructureTile(key, results, true); }
                        var position = spread.getStartChunk(seed, x * spacing, z * spacing);
                        if (position.x >= minX && position.x <= maxX && position.z >= minZ && position.z <= maxZ
                                && placement.shouldGenerate(calculator, position.x, position.z)
                                && append(key, set, placement, position, results)) { return new StructureTile(key, results, true); }
                    }
                }
            } else if (placement instanceof ConcentricRingsStructurePlacement rings) {
                List<ChunkPos> positions = calculator.getPlacementPositions(rings);
                if (positions == null) { continue; }
                for (var position : positions) {
                    token.check();
                    if (position.x >= minX && position.x <= maxX && position.z >= minZ && position.z <= maxZ
                            && placement.shouldGenerate(calculator, position.x, position.z)
                            && append(key, set, placement, position, results)) { return new StructureTile(key, results, true); }
                }
            } else {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int x = minX; x <= maxX; x++) {
                        token.check();
                        if (remaining-- <= 0) { return new StructureTile(key, results, true); }
                        if (placement.shouldGenerate(calculator, x, z)
                                && append(key, set, placement, new ChunkPos(x, z), results)) { return new StructureTile(key, results, true); }
                    }
                }
            }
        }
        return new StructureTile(key, results, false);
    }

    private static boolean append(TileKey key, StructureSet set, StructurePlacement placement,
                                   ChunkPos chunk, List<PreviewStructure> output) {
        var position = placement.getLocatePos(chunk);
        for (var entry : set.structures()) {
            var nativeId = entry.structure().getKey();
            if (nativeId.isEmpty()) { continue; }
            var id = new ResourceId(nativeId.get().getValue().toString());
            if (!key.request().structures().ids().isEmpty() && !key.request().structures().ids().contains(id)) { continue; }
            if (output.size() == key.request().structures().maximumResults()) { return true; }
            output.add(new PreviewStructure(id, position.getX(), position.getY(), position.getZ(), SupportLevel.ESTIMATED));
        }
        return false;
    }
}
