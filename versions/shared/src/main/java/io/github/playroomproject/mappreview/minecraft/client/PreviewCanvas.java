package io.github.playroomproject.mappreview.minecraft.client;

import io.github.playroomproject.mappreview.client.render.RenderUploadQueue;
import io.github.playroomproject.mappreview.client.render.TileColorizer;
import io.github.playroomproject.mappreview.config.PreviewConfig;
import io.github.playroomproject.mappreview.core.api.PreviewContext;
import io.github.playroomproject.mappreview.core.api.ResourceId;
import io.github.playroomproject.mappreview.core.camera.PreviewCamera;
import io.github.playroomproject.mappreview.core.color.BiomeColors;
import io.github.playroomproject.mappreview.core.filter.PreviewFilter;
import io.github.playroomproject.mappreview.core.scheduler.HardwareProfile;
import io.github.playroomproject.mappreview.core.scheduler.PreviewSession;
import io.github.playroomproject.mappreview.core.scheduler.TileEngine;
import io.github.playroomproject.mappreview.core.scheduler.ViewportPlanner;
import io.github.playroomproject.mappreview.core.tile.DataLayer;
import io.github.playroomproject.mappreview.core.tile.RasterTile;
import io.github.playroomproject.mappreview.core.tile.StructureTile;
import io.github.playroomproject.mappreview.core.tile.TileKey;
import io.github.playroomproject.mappreview.core.worldgen.BackendFactory;
import io.github.playroomproject.mappreview.core.worldgen.HeightMode;
import io.github.playroomproject.mappreview.core.worldgen.StructureQuery;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import io.github.playroomproject.mappreview.client.render.TileFailures;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;

/** Composable map surface. All public controls run on the client thread. */
public final class PreviewCanvas implements AutoCloseable {
    public enum View {
        BIOMES("Biomes", DataLayer.BIOMES, TileColorizer.Style.BIOMES),
        HEIGHT("Raw height", DataLayer.HEIGHT, TileColorizer.Style.HEIGHT),
        TOPOGRAPHY("Topography", DataLayer.HEIGHT, TileColorizer.Style.TOPOGRAPHY),
        SLOPE("Slope", DataLayer.HEIGHT, TileColorizer.Style.SLOPE),
        LAND_OCEAN("Land / ocean", DataLayer.HEIGHT, TileColorizer.Style.LAND_OCEAN),
        CAVE_BIOMES("Cave biomes", DataLayer.CAVE_BIOMES, TileColorizer.Style.BIOMES),
        DENSITY("Caves: raw density", DataLayer.CAVE_DENSITY, null),
        SURFACE("Raw column surface", DataLayer.SURFACE, null),
        SLIME("Slime chunks", DataLayer.SLIME_CHUNKS, null);
        public final String label;
        public final DataLayer layer;
        final TileColorizer.Style style;
        View(String label, DataLayer layer, TileColorizer.Style style) { this.label = label; this.layer = layer; this.style = style; }
    }

    private final PreviewConfig config;
    private final TileEngine engine;
    private final ViewportPlanner planner;
    private final AtlasPreviewRenderer renderer = new AtlasPreviewRenderer();
    private final RenderUploadQueue uploads;
    private final ThreadPoolExecutor colors = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(64), runnable -> {
                Thread thread = new Thread(runnable, "Map PreView colors"); thread.setDaemon(true); return thread;
            });
    private final Set<DisplayKey> inFlight = ConcurrentHashMap.newKeySet();
    private final Set<DisplayKey> awaitingUpload = ConcurrentHashMap.newKeySet();
    private final Set<DisplayKey> failed = ConcurrentHashMap.newKeySet();
    private final Map<TileKey, StructureTile> structures = new ConcurrentHashMap<>();
    private final AtomicReference<String> failure = new AtomicReference<>("");
    private PreviewCamera camera = new PreviewCamera(0, 0, 4, 320, 160);
    private PreviewSession session;
    private BiomeColors biomeColors;
    private Map<String, String> individualColors;
    private Map<String, String> tagColors;
    private View view = View.BIOMES;
    private int y = 64;
    private int left;
    private int top;
    private volatile long revision;
    private volatile boolean showStructures;
    private volatile boolean closed;
    private String biomeFilter = "";

    public PreviewCanvas(PreviewConfig config) {
        this.config = config;
        individualColors = config.biomeColors();
        tagColors = config.biomeTagColors();
        engine = new TileEngine(config.engineLimits(HardwareProfile.detectJvm()));
        planner = new ViewportPlanner(config.tileSize(), config.lodSteps(), config.prefetchRings(), config.maximumViewportTiles());
        uploads = new RenderUploadQueue(256, config.uploadQueueMiB() * 1_048_576L);
    }

    public void activate(PreviewContext context, BackendFactory factory) {
        session = engine.beginSession(context, factory);
        y = Math.max(context.dimension().minY(), Math.min(context.dimension().maxYExclusive() - 1, y));
        if (!session.capabilities().supports(view.layer)) { view = View.BIOMES; }
        setColors(individualColors, tagColors);
        structures.clear();
        failure.set("");
    }

    public void setColors(Map<String, String> individual, Map<String, String> tags) {
        individualColors = Map.copyOf(individual);
        tagColors = Map.copyOf(tags);
        if (session == null) { return; }
        Map<ResourceId, Integer> ids = new HashMap<>();
        Map<ResourceId, Integer> tagIds = new HashMap<>();
        individual.forEach((id, hex) -> ids.put(new ResourceId(id), BiomeColors.parseHex(hex)));
        tags.forEach((id, hex) -> tagIds.put(new ResourceId(id), BiomeColors.parseHex(hex)));
        biomeColors = new BiomeColors(session.context().biomes(), ids, tagIds);
        invalidateDisplay();
    }

    public void cancelSession() {
        engine.cancelSession();
        failure.set("");
        long cancelledEpoch = session == null ? 0 : session.epoch();
        session = null;
        revision++;
        uploads.activate(cancelledEpoch + 1, revision);
        renderer.clear();
        colors.getQueue().clear();
        inFlight.clear();
        awaitingUpload.clear();
        failed.clear();
        structures.clear();
    }

    private void invalidateDisplay() {
        revision++;
        failure.set("");
        if (session != null) { uploads.activate(session.epoch(), revision); }
        renderer.clear();
        colors.getQueue().clear();
        inFlight.clear();
        awaitingUpload.clear();
        failed.clear();
    }

    public void setBounds(int x, int z, int width, int height) {
        left = x; top = z;
        camera = new PreviewCamera(camera.centerX(), camera.centerZ(), camera.blocksPerPixel(),
                Math.max(1, width), Math.max(1, height));
    }
    public void setCamera(PreviewCamera camera) { this.camera = camera; }
    public PreviewCamera camera() { return camera; }
    public boolean contains(double x, double z) { return x >= left && x < left + camera.width() && z >= top && z < top + camera.height(); }
    public void pan(double dx, double dz) { camera = camera.pan(dx, dz); }
    public void zoom(double x, double z, double amount) { camera = camera.zoomAt(x - left, z - top, Math.pow(1.25, -amount)); }
    public void reset() { camera = new PreviewCamera(0, 0, 4, camera.width(), camera.height()); }
    public void setY(int value) {
        if (session == null) { return; }
        int next = Math.max(session.context().dimension().minY(), Math.min(session.context().dimension().maxYExclusive() - 1, value));
        if (next != y) { y = next; if (view.layer.usesY()) { invalidateDisplay(); } }
    }
    public int y() { return y; }
    public View view() { return view; }
    public void cycleView() {
        if (session == null) { return; }
        for (int i = 1; i <= View.values().length; i++) {
            View next = View.values()[(view.ordinal() + i) % View.values().length];
            if (session.capabilities().supports(next.layer)) { view = next; invalidateDisplay(); return; }
        }
    }
    public void setBiomeFilter(String filter) { biomeFilter = filter.trim().toLowerCase(java.util.Locale.ROOT); invalidateDisplay(); }
    public void toggleStructures() { showStructures = !showStructures; structures.clear(); }
    public boolean structuresVisible() { return showStructures; }
    public PreviewContext context() { return session == null ? null : session.context(); }
    public String failure() { return failure.get(); }
    public TileEngine.Stats stats() { return engine.stats(); }
    public int visibleTiles() { return renderer.size(); }

    public void tick() {
        if (session == null || closed) { return; }
        var plans = new ArrayList<>(planner.plan(camera, view.layer, y, HeightMode.WORLD_SURFACE, StructureQuery.DEFAULT));
        if (showStructures && session.capabilities().supports(DataLayer.STRUCTURE_CANDIDATES)) {
            plans.addAll(planner.plan(camera, DataLayer.STRUCTURE_CANDIDATES, 0, HeightMode.WORLD_SURFACE, StructureQuery.DEFAULT));
        }
        Set<TileKey> retained = new HashSet<>();
        plans.forEach(plan -> retained.add(session.key(plan.request())));
        engine.retainRequests(session, retained);
        structures.keySet().retainAll(retained);
        failed.removeIf(key -> key.revision != revision || !retained.contains(key.tile));
        for (var plan : plans) {
            var key = session.key(plan.request());
            boolean structureLayer = plan.request().layer().structures();
            if (structureLayer ? structures.containsKey(key) : renderer.contains(key)) { continue; }
            var displayKey = new DisplayKey(key, revision);
            if (failed.contains(displayKey) || inFlight.size() >= 64 || !inFlight.add(displayKey)) { continue; }
            final PreviewSession requestedSession = session;
            final long requestedRevision = revision;
            final View requestedView = view;
            final BiomeColors requestedColors = biomeColors;
            final String requestedFilter = biomeFilter;
            engine.request(requestedSession, plan.request(), plan.priority()).whenComplete((tile, error) -> {
                if (error != null || closed || requestedSession.isCancelled() || requestedRevision != revision) {
                    inFlight.remove(displayKey);
                    if (error != null) { reportFailure(displayKey, requestedSession, error); }
                    return;
                }
                if (tile instanceof StructureTile found) {
                    if (!closed && !requestedSession.isCancelled() && showStructures) { structures.put(key, found); }
                    inFlight.remove(displayKey);
                    return;
                }
                try {
                    colors.execute(() -> {
                        boolean queued = false;
                        try {
                            if (closed || requestedSession.isCancelled() || requestedRevision != revision) { return; }
                            var raster = (RasterTile) tile;
                            PreviewFilter filter = new PreviewFilter() {
                                @Override public int color(int biome, int height, double slope, int argb) {
                                    if (requestedFilter.isEmpty() || biome < 0 || requestedSession.context().biomes().biome(biome)
                                            .id().value().contains(requestedFilter)) { return argb; }
                                    int gray = ((argb >>> 16 & 255) + (argb >>> 8 & 255) + (argb & 255)) / 12;
                                    return 0xff000000 | gray << 16 | gray << 8 | gray;
                                }
                            };
                            var colored = requestedView.style != null
                                    ? TileColorizer.colorize(requestedView.style == TileColorizer.Style.BIOMES ? raster : null,
                                        requestedView.style == TileColorizer.Style.BIOMES ? null : raster, requestedColors,
                                        requestedView.style, requestedSession.context().dimension().seaLevel(), filter)
                                    : TileColorizer.raw(raster, value -> rawColor(requestedView, value));
                            // Register before publication; the client may drain immediately after offer.
                            awaitingUpload.add(displayKey);
                            queued = uploads.offer(requestedSession.epoch(), requestedRevision, colored);
                        } catch (RuntimeException exception) { reportFailure(displayKey, requestedSession, exception); }
                        finally { if (!queued) { awaitingUpload.remove(displayKey); inFlight.remove(displayKey); } }
                    });
                } catch (RejectedExecutionException ignored) { inFlight.remove(displayKey); }
            });
        }
    }

    private void reportFailure(DisplayKey key, PreviewSession source, Throwable error) {
        TileFailures.reportable(error).ifPresent(cause -> MinecraftClient.getInstance().execute(() -> {
            // Check the display identity on its owning thread before publishing a worker failure.
            if (!closed && source == session && !source.isCancelled() && key.revision == revision) {
                failed.add(key);
                failure.compareAndSet("", message(cause));
            }
        }));
    }

    private static int rawColor(View view, int value) {
        return switch (view) {
            case SLIME -> value == 0 ? 0xff172327 : 0xff66dd55;
            case DENSITY -> Float.intBitsToFloat(value) <= 0 ? 0xff263b55 : 0xffb2a28a;
            case SURFACE -> {
                var state = Block.getStateFromRawId(value);
                yield 0xff000000 | state.getMapColor(net.minecraft.world.EmptyBlockView.INSTANCE, BlockPos.ORIGIN).color;
            }
            default -> throw new IllegalArgumentException("Unsupported raw display");
        };
    }

    public void render(DrawContext context) {
        context.fill(left, top, left + camera.width(), top + camera.height(), 0xff142026);
        uploads.drain(renderer, config.uploadsPerFrame());
        awaitingUpload.removeIf(key -> {
            if (key.revision != revision || renderer.contains(key.tile)) { inFlight.remove(key); return true; }
            return false;
        });
        renderer.frame(context, left, top);
        renderer.draw(camera);
        if (showStructures) {
            context.enableScissor(left, top, left + camera.width(), top + camera.height());
            for (var tile : structures.values()) {
                for (var structure : tile.structures()) {
                    int x = left + (int) ((structure.x() - camera.worldX(0)) / camera.blocksPerPixel());
                    int z = top + (int) ((structure.z() - camera.worldZ(0)) / camera.blocksPerPixel());
                    context.fill(x - 2, z - 2, x + 3, z + 3, 0xff17130a);
                    context.fill(x - 1, z - 1, x + 2, z + 2, 0xffffd45b);
                }
            }
            context.disableScissor();
        }
    }

    public String hover(double screenX, double screenZ) {
        if (session == null || !contains(screenX, screenZ)) { return ""; }
        int x = (int) Math.floor(camera.worldX(screenX - left));
        int z = (int) Math.floor(camera.worldZ(screenZ - top));
        String base = "X " + x + "  Z " + z + (view.layer.usesY() ? "  Y " + y : "");
        var hoverPlans = planner.plan(camera, view.layer, y, HeightMode.WORLD_SURFACE, StructureQuery.DEFAULT);
        for (int planIndex = hoverPlans.size() - 1; planIndex >= 0; planIndex--) {
            var plan = hoverPlans.get(planIndex);
            var request = plan.request();
            if (x < request.originX() || z < request.originZ() || x >= request.originX() + request.tileSize()
                    || z >= request.originZ() + request.tileSize()) { continue; }
            var data = engine.cache().get(session.key(request));
            if (!(data instanceof RasterTile raster)) { continue; }
            int value = raster.value(Math.floorDiv(x - request.originX(), request.step()) + view.layer.border(),
                    Math.floorDiv(z - request.originZ(), request.step()) + view.layer.border());
            if (view.layer == DataLayer.BIOMES || view.layer == DataLayer.CAVE_BIOMES) {
                return base + "  " + session.context().biomes().biome(value).id().value();
            }
            return base + (view.layer == DataLayer.HEIGHT ? "  Height " + value : "");
        }
        return base;
    }

    private static String message(Throwable error) {
        while (error.getCause() != null && error instanceof java.util.concurrent.CompletionException) { error = error.getCause(); }
        return error.getClass().getSimpleName() + ": " + java.util.Objects.toString(error.getMessage(), "Sampling failed");
    }
    @Override public void close() {
        if (closed) { return; }
        closed = true;
        engine.close();
        colors.shutdownNow();
        renderer.close();
        structures.clear();
        inFlight.clear();
        awaitingUpload.clear();
        failed.clear();
    }
    private record DisplayKey(TileKey tile, long revision) { }
}
