package io.github.playroomproject.mappreview.config;

import static org.junit.jupiter.api.Assertions.*;

import io.github.playroomproject.mappreview.core.cache.Fingerprint;
import io.github.playroomproject.mappreview.core.scheduler.HardwareProfile;
import io.github.playroomproject.mappreview.core.scheduler.PerformanceMode;
import io.github.playroomproject.mappreview.pregen.ChunkPos;
import io.github.playroomproject.mappreview.pregen.PregenCheckpoint;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConfigurationTest {
    @TempDir Path directory;
    private final AtomicJsonStore store = new AtomicJsonStore();
    private final HardwareProfile hardware = new HardwareProfile(8, 2L * 1024 * 1024 * 1024, "unknown", "unknown");

    @Test void configurationRoundTripsThroughStrictJson() throws IOException {
        var config = PreviewConfig.defaults(hardware);
        Path file = directory.resolve("map-preview.json");
        store.write(file, config, false);
        assertEquals(config, store.read(file, PreviewConfig.class));
        assertTrue(Files.readString(file).contains("Map PreView"));
    }

    @Test void checkpointRoundTripPreservesUnfinishedDispatchedChunks() throws IOException {
        var checkpoint = new PregenCheckpoint(1, Fingerprint.builder().add("job").finish(), 15, 12, 1, 5000,
                List.of(new ChunkPos(-1, -2), new ChunkPos(-3, 4), new ChunkPos(3, 8)));
        Path file = directory.resolve("pregen.json");
        store.write(file, checkpoint, false);
        assertEquals(checkpoint, store.read(file, PregenCheckpoint.class));
    }

    @Test void explicitApplyKeepsAnExactBackupAndLeavesNoTemporaryFiles() throws IOException {
        Path file = directory.resolve("map-preview.json");
        store.write(file, Map.of("name", "Map PreView", "revision", 1), false);
        String previous = Files.readString(file);
        store.write(file, Map.of("name", "Map PreView", "revision", 2), true);
        assertEquals(previous, Files.readString(directory.resolve("map-preview.json.bak")));
        assertNotEquals(previous, Files.readString(file));
        try (var files = Files.list(directory)) { assertEquals(2, files.count()); }
    }

    @ParameterizedTest @ValueSource(strings = {"{\"a\":1,\"a\":2}", "{\"a\":NaN}", "{\"a\":1} {}", "{\"a\":1,}", "// comment\n{}"})
    void malformedOrAmbiguousJsonIsRejected(String json) throws IOException {
        Path file = directory.resolve("invalid.json");
        Files.writeString(file, json);
        assertThrows(IOException.class, () -> store.read(file, PreviewConfig.class));
    }

    @Test void unknownOrMissingFieldsDoNotGetSilentlyOverwritten() throws IOException {
        Path file = directory.resolve("config.json");
        store.write(file, PreviewConfig.defaults(hardware), false);
        String valid = Files.readString(file);
        Files.writeString(file, valid.replace("\"schemaVersion\": 1", "\"schemaVersion\": 1, \"futureField\": true"));
        assertThrows(IOException.class, () -> store.read(file, PreviewConfig.class));
        Files.writeString(file, valid.replace("\"schemaVersion\": 1,", ""));
        assertThrows(IOException.class, () -> store.read(file, PreviewConfig.class));
    }

    @Test void futureSchemaVersionAndBadColorsFailRecordValidation() throws IOException {
        Path file = directory.resolve("config.json");
        store.write(file, PreviewConfig.defaults(hardware), false);
        String valid = Files.readString(file);
        Files.writeString(file, valid.replace("\"schemaVersion\": 1", "\"schemaVersion\": 99"));
        assertThrows(IOException.class, () -> store.read(file, PreviewConfig.class));
        Files.writeString(file, valid.replace("\"biomeColors\": {}", "\"biomeColors\": {\"minecraft:plains\": \"red\"}"));
        assertThrows(IOException.class, () -> store.read(file, PreviewConfig.class));
    }

    @Test void oversizedAndDeeplyNestedFilesAreRejected() throws IOException {
        Path file = directory.resolve("config.json");
        Files.writeString(file, " ".repeat(AtomicJsonStore.MAXIMUM_BYTES + 1));
        assertThrows(IOException.class, () -> store.read(file, PreviewConfig.class));
        Files.writeString(file, "[".repeat(40) + "0" + "]".repeat(40));
        assertThrows(IOException.class, () -> store.read(file, PreviewConfig.class));
    }

    @Test void invalidUtf8IsRejected() throws IOException {
        Path file = directory.resolve("config.json");
        Files.write(file, new byte[] {(byte) 0xc3, (byte) 0x28});
        assertThrows(IOException.class, () -> store.read(file, PreviewConfig.class));
    }

    @Test void automaticBudgetsReserveResourcesAndClampHeapUse() {
        var single = new HardwareProfile(1, 128 * 1024 * 1024L, "unknown", "unknown");
        var limits = PreviewConfig.defaults(single).engineLimits(single);
        assertEquals(1, limits.workers());
        assertEquals(single.maximumHeapBytes() / 4, limits.cacheBytes());
        assertTrue(PerformanceMode.BALANCED.workers(hardware) < hardware.logicalProcessors());
        assertEquals(8, PerformanceMode.MAX.workers(hardware));
    }
}
