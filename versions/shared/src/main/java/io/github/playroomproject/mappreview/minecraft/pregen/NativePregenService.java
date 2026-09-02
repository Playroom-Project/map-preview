package io.github.playroomproject.mappreview.minecraft.pregen;

import io.github.playroomproject.mappreview.config.AtomicJsonStore;
import io.github.playroomproject.mappreview.core.MapPreView;
import io.github.playroomproject.mappreview.core.api.ResourceId;
import io.github.playroomproject.mappreview.core.cache.Fingerprint;
import io.github.playroomproject.mappreview.pregen.ChunkAreas;
import io.github.playroomproject.mappreview.pregen.ChunkBounds;
import io.github.playroomproject.mappreview.pregen.ChunkPlan;
import io.github.playroomproject.mappreview.pregen.ChunkPos;
import io.github.playroomproject.mappreview.pregen.PregenCheckpoint;
import io.github.playroomproject.mappreview.pregen.PregenController;
import io.github.playroomproject.mappreview.pregen.PregenProgress;
import io.github.playroomproject.mappreview.pregen.PregenSettings;
import io.github.playroomproject.mappreview.pregen.PregenState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

/** One server-owned job with safe pause/save/resume and a conservative restart checkpoint. */
public final class NativePregenService {
    private final MinecraftServer server;
    private final NativePregenBridge bridge;
    private final AtomicJsonStore store = new AtomicJsonStore();
    private final Path directory;
    private final Map<String, String> installedMods;
    private PregenController controller;
    private JobSpec specification;
    private volatile PregenProgress progress;
    private volatile String message = "No pregeneration job";
    private boolean checkpointPending;
    private boolean resumeAfterCheckpoint;
    private long lastCheckpoint;
    private boolean finalCheckpointSaved;
    private long nextCheckpointAttempt;

    public NativePregenService(MinecraftServer server, Map<String, String> installedMods) {
        this.server = server;
        this.installedMods = Map.copyOf(installedMods);
        bridge = new NativePregenBridge(server);
        directory = server.getSavePath(WorldSavePath.ROOT).resolve("map_preview");
        if (Files.isRegularFile(directory.resolve("pregen.json"))) { message = "A saved pregeneration job is available. Resume it explicitly."; }
    }
    public MinecraftServer server() { return server; }
    public PregenProgress progress() { return progress; }
    public String message() { return message; }

    public void start(JobSpec job) throws IOException {
        bridge.assertServerThread();
        if (controller != null && (!terminal(controller.progress().state()) || controller.progress().inFlight() != 0)) {
            throw new IllegalStateException("Cancel and drain the current job first");
        }
        if (job.radiusBlocks() < 0 || job.radiusBlocks() > 1_000_000 || Math.abs((long) job.centerX()) + job.radiusBlocks() > 29_999_984
                || Math.abs((long) job.centerZ()) + job.radiusBlocks() > 29_999_984) { throw new IllegalArgumentException("Pregeneration exceeds the supported world border"); }
        specification = job;
        finalCheckpointSaved = false;
        checkpointPending = false;
        resumeAfterCheckpoint = false;
        nextCheckpointAttempt = 0;
        controller = createController(job);
        persistCheckpoint();
        controller.start();
        progress = controller.progress();
        message = "Generating chunks";
    }

    private PregenController createController(JobSpec job) throws IOException {
        var area = switch (job.shape()) {
            case "square" -> ChunkAreas.rectangle(new ChunkBounds(Math.floorDiv(job.centerX() - job.radiusBlocks(), 16),
                    Math.floorDiv(job.centerZ() - job.radiusBlocks(), 16), Math.floorDiv(job.centerX() + job.radiusBlocks(), 16),
                    Math.floorDiv(job.centerZ() + job.radiusBlocks(), 16)));
            case "circle" -> ChunkAreas.circle(Math.floorDiv(job.centerX(), 16), Math.floorDiv(job.centerZ(), 16),
                    Math.floorDiv(job.radiusBlocks() + 15, 16));
            case "polygon" -> ChunkAreas.polygon(job.vertices());
            default -> throw new IllegalArgumentException("Unknown pregeneration shape");
        };
        var plan = new ChunkPlan(area, job.traversal());
        var worldIdFile = directory.resolve("world-id.json");
        WorldIdentity identity;
        if (Files.exists(worldIdFile)) { identity = store.read(worldIdFile, WorldIdentity.class); }
        else { identity = new WorldIdentity(UUID.randomUUID().toString()); store.write(worldIdFile, identity, false); }
        var registryFingerprint = io.github.playroomproject.mappreview.minecraft.worldgen.NativeRegistryFingerprint.capture(server.getRegistryManager());
        var worldHash = Fingerprint.builder().add(identity.id()).add(registryFingerprint.hex())
                .add(net.minecraft.SharedConstants.getGameVersion().getName())
                .add(server.getSaveProperties().getGeneratorOptions().getSeed())
                .add(Boolean.toString(server.getSaveProperties().getGeneratorOptions().shouldGenerateStructures()));
        worldHash.add(installedMods.size());
        installedMods.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> worldHash.add(entry.getKey()).add(entry.getValue()));
        var packs = server.getSaveProperties().getDataConfiguration().dataPacks().getEnabled();
        worldHash.add(packs.size());
        packs.forEach(worldHash::add);
        var ops = net.minecraft.registry.RegistryOps.of(com.mojang.serialization.JsonOps.INSTANCE, server.getRegistryManager());
        java.util.stream.StreamSupport.stream(server.getWorlds().spliterator(), false)
                .sorted(java.util.Comparator.comparing(world -> world.getRegistryKey().getValue().toString())).forEach(world -> {
                    var encoded = net.minecraft.world.gen.chunk.ChunkGenerator.CODEC.encodeStart(ops, world.getChunkManager().getChunkGenerator())
                            .result().orElseThrow(() -> new IllegalArgumentException("The active generator cannot be fingerprinted"));
                    worldHash.add(world.getRegistryKey().getValue().toString()).add(encoded.toString());
                });
        var worldIdentity = worldHash.finish();
        return new PregenController(bridge, worldIdentity, new ResourceId(job.dimension()), plan,
                new PregenSettings(job.maximumInFlight(), 2), System::nanoTime);
    }

    public void pause() {
        bridge.assertServerThread();
        requireJob().pause();
        checkpointPending = true;
        resumeAfterCheckpoint = false;
        message = "Pausing after current chunks finish";
    }
    public void resume() throws IOException {
        bridge.assertServerThread();
        if (controller != null && controller.progress().state() == PregenState.PAUSED) {
            controller.resume(); message = "Generating chunks"; return;
        }
        if (controller != null && (!terminal(controller.progress().state()) || controller.progress().inFlight() != 0)) {
            throw new IllegalStateException("The current job has not drained yet");
        }
        var saved = store.read(directory.resolve("pregen.json"), SavedJob.class);
        specification = saved.specification();
        finalCheckpointSaved = false;
        controller = createController(specification);
        controller.restore(saved.checkpoint());
        controller.start();
        message = "Resumed saved pregeneration";
    }
    public void cancel() {
        bridge.assertServerThread();
        requireJob().cancel();
        checkpointPending = true;
        resumeAfterCheckpoint = false;
        message = "Cancelling after current chunks finish";
    }
    public void onPlayerJoin() {
        bridge.assertServerThread();
        if (controller != null && controller.progress().state() == PregenState.RUNNING) { cancel(); }
    }
    public void tick() {
        bridge.assertServerThread();
        if (controller == null) { return; }
        controller.tick();
        progress = controller.progress();
        if (terminal(progress.state()) && !finalCheckpointSaved && System.nanoTime() >= nextCheckpointAttempt) { checkpointPending = true; }
        if (progress.state() == PregenState.RUNNING && System.nanoTime() - lastCheckpoint >= 60_000_000_000L) {
            controller.pause(); checkpointPending = true; resumeAfterCheckpoint = true;
        }
        if (checkpointPending && progress.inFlight() == 0 && (progress.state() == PregenState.PAUSED || terminal(progress.state()))) {
            checkpointPending = false;
            try {
                persistCheckpoint();
                if (terminal(progress.state())) { finalCheckpointSaved = true; }
                if (resumeAfterCheckpoint && progress.state() == PregenState.PAUSED) { controller.resume(); }
                else { message = "Pregeneration " + progress.state().name().toLowerCase(java.util.Locale.ROOT) + "; progress saved"; }
            } catch (IOException | RuntimeException exception) {
                nextCheckpointAttempt = System.nanoTime() + 60_000_000_000L;
                message = "Could not save pregeneration: " + exception.getMessage();
                MapPreView.LOGGER.log(System.Logger.Level.ERROR, message, exception);
            }
        }
        if (progress.state() == PregenState.COMPLETED && finalCheckpointSaved) { message = "Pregeneration completed and chunks saved"; }
    }

    private void persistCheckpoint() throws IOException {
        var checkpoint = controller.checkpoint().toCompletableFuture().join();
        store.write(directory.resolve("pregen.json"), new SavedJob(1, specification, checkpoint), true);
        lastCheckpoint = System.nanoTime();
    }
    public void stop() {
        bridge.assertServerThread();
        if (controller != null) {
            controller.cancel();
            controller.tick();
            if (controller.progress().inFlight() == 0 && terminal(controller.progress().state())) {
                try { persistCheckpoint(); }
                catch (IOException | RuntimeException exception) { MapPreView.LOGGER.log(System.Logger.Level.WARNING,
                        "Map PreView retains the last safe checkpoint after shutdown", exception); }
            }
        }
        bridge.releaseTickets();
    }
    private PregenController requireJob() {
        if (controller == null) { throw new IllegalStateException("No pregeneration job is active"); }
        return controller;
    }
    private static boolean terminal(PregenState state) { return state == PregenState.CANCELLED || state == PregenState.COMPLETED || state == PregenState.FAILED; }

    public record JobSpec(String dimension, String shape, int centerX, int centerZ, int radiusBlocks,
                          List<ChunkPos> vertices, ChunkPlan.Traversal traversal, int maximumInFlight) {
        public JobSpec {
            new ResourceId(dimension);
            if (!List.of("square", "circle", "polygon").contains(shape) || radiusBlocks < 0 || radiusBlocks > 1_000_000) {
                throw new IllegalArgumentException("Invalid pregeneration shape or radius");
            }
            vertices = List.copyOf(vertices);
            if (vertices.size() > 1024 || vertices.stream().anyMatch(point -> Math.abs((long) point.x()) > 1_874_998
                    || Math.abs((long) point.z()) > 1_874_998)) { throw new IllegalArgumentException("Polygon exceeds the supported world border"); }
            if (shape.equals("polygon") && !vertices.isEmpty()) {
                long rows = (long) vertices.stream().mapToInt(ChunkPos::z).max().orElseThrow()
                        - vertices.stream().mapToInt(ChunkPos::z).min().orElseThrow();
                if (rows * vertices.size() > 2_000_000) {
                    throw new IllegalArgumentException("Polygon planning exceeds the per-job budget; split it into smaller areas");
                }
            }
            java.util.Objects.requireNonNull(traversal, "traversal");
            new PregenSettings(maximumInFlight, 2);
        }
    }
    public record SavedJob(int schemaVersion, JobSpec specification, PregenCheckpoint checkpoint) {
        public SavedJob { if (schemaVersion != 1) { throw new IllegalArgumentException("Unsupported pregeneration checkpoint version"); } }
    }
    public record WorldIdentity(String id) { public WorldIdentity { UUID.fromString(id); } }
}
