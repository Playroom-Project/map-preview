package io.github.playroomproject.mappreview.minecraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.playroomproject.mappreview.client.render.ColoredTile;
import io.github.playroomproject.mappreview.client.render.PreviewRenderer;
import io.github.playroomproject.mappreview.core.camera.PreviewCamera;
import io.github.playroomproject.mappreview.core.tile.TileKey;
import io.github.playroomproject.mappreview.core.tile.TileRequest;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/** A 16 MiB texture atlas with aligned variable-sized slots and one draw call per map frame. */
public final class AtlasPreviewRenderer implements PreviewRenderer {
    private static final int SIDE = 2048;
    private static final int UNIT = 32;
    private static final int GRID = SIDE / UNIT;
    private final NativeImage image = new NativeImage(SIDE, SIDE, false);
    private final NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
    private final Identifier id;
    private final boolean[] occupied = new boolean[GRID * GRID];
    private final Map<Region, Slot> tiles = new LinkedHashMap<>(128, 0.75f, true);
    private DrawContext context;
    private int left;
    private int top;
    private boolean closed;

    public AtlasPreviewRenderer() {
        RenderSystem.assertOnRenderThread();
        id = MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("map_preview/atlas", texture);
    }

    public void frame(DrawContext context, int left, int top) {
        this.context = context;
        this.left = left;
        this.top = top;
    }

    public boolean contains(TileKey key) {
        Slot slot = tiles.get(Region.of(key.request()));
        return slot != null && slot.key.sessionFingerprint().equals(key.sessionFingerprint())
                && slot.key.request().step() <= key.request().step();
    }

    @Override public void upload(ColoredTile tile) {
        RenderSystem.assertOnRenderThread();
        if (closed || contains(tile.key())) { return; }
        var region = Region.of(tile.key().request());
        Slot previous = tiles.remove(region);
        if (previous != null) { mark(previous, false); }
        int units = Math.max(1, tile.side() / UNIT);
        Slot slot;
        while ((slot = allocate(tile.key(), units)) == null) {
            var oldest = tiles.entrySet().iterator();
            if (!oldest.hasNext()) { throw new IllegalStateException("The preview tile exceeds the atlas capacity"); }
            mark(oldest.next().getValue(), false);
            oldest.remove();
        }
        int x = slot.x * UNIT;
        int y = slot.y * UNIT;
        var pixels = tile.pixels();
        for (int row = 0; row < tile.side(); row++) {
            for (int column = 0; column < tile.side(); column++) {
                int argb = pixels.get(row * tile.side() + column);
                int abgr = argb & 0xff00ff00 | (argb & 255) << 16 | (argb >>> 16 & 255);
                image.setColor(x + column, y + row, abgr);
            }
        }
        texture.bindTexture();
        image.upload(0, x, y, x, y, tile.side(), tile.side(), false, false, false, false);
        tiles.put(region, slot);
    }

    private Slot allocate(TileKey key, int units) {
        for (int y = 0; y <= GRID - units; y += units) {
            for (int x = 0; x <= GRID - units; x += units) {
                boolean free = true;
                for (int row = y; row < y + units && free; row++) {
                    for (int column = x; column < x + units; column++) {
                        if (occupied[row * GRID + column]) { free = false; break; }
                    }
                }
                if (free) {
                    Slot slot = new Slot(key, x, y, units);
                    mark(slot, true);
                    return slot;
                }
            }
        }
        return null;
    }

    private void mark(Slot slot, boolean value) {
        for (int y = slot.y; y < slot.y + slot.units; y++) {
            for (int x = slot.x; x < slot.x + slot.units; x++) { occupied[y * GRID + x] = value; }
        }
    }

    @Override public void draw(PreviewCamera camera) {
        RenderSystem.assertOnRenderThread();
        if (closed || tiles.isEmpty()) { return; }
        context.draw();
        context.enableScissor(left, top, left + camera.width(), top + camera.height());
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.setShaderTexture(0, id);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        var matrix = context.getMatrices().peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        // Larger world tiles are drawn first so finer regions cover their coarser parents.
        tiles.values().stream().sorted(Comparator.comparingInt((Slot slot) -> slot.key.request().tileSize()).reversed()).forEach(slot -> {
            var request = slot.key.request();
            float x0 = (float) (left + (request.originX() - camera.worldX(0)) / camera.blocksPerPixel());
            float y0 = (float) (top + (request.originZ() - camera.worldZ(0)) / camera.blocksPerPixel());
            float span = (float) (request.tileSize() / camera.blocksPerPixel());
            float x1 = x0 + span;
            float y1 = y0 + span;
            if (x1 < left || y1 < top || x0 > left + camera.width() || y0 > top + camera.height()) { return; }
            float u0 = slot.x * UNIT / (float) SIDE;
            float v0 = slot.y * UNIT / (float) SIDE;
            float u1 = u0 + request.cells() / (float) SIDE;
            float v1 = v0 + request.cells() / (float) SIDE;
            buffer.vertex(matrix, x0, y1, 0).texture(u0, v1).color(255, 255, 255, 255).next();
            buffer.vertex(matrix, x1, y1, 0).texture(u1, v1).color(255, 255, 255, 255).next();
            buffer.vertex(matrix, x1, y0, 0).texture(u1, v0).color(255, 255, 255, 255).next();
            buffer.vertex(matrix, x0, y0, 0).texture(u0, v0).color(255, 255, 255, 255).next();
        });
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.disableBlend();
        context.disableScissor();
    }

    @Override public void release(TileKey key) {
        RenderSystem.assertOnRenderThread();
        Slot slot = tiles.get(Region.of(key.request()));
        if (slot != null && slot.key.equals(key)) { tiles.remove(Region.of(key.request())); mark(slot, false); }
    }

    public void clear() { tiles.clear(); java.util.Arrays.fill(occupied, false); }
    public int size() { return tiles.size(); }
    @Override public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) { return; }
        closed = true;
        clear();
        MinecraftClient.getInstance().getTextureManager().destroyTexture(id);
    }

    private record Slot(TileKey key, int x, int y, int units) { }
    private record Region(int x, int z, int span) {
        static Region of(TileRequest request) { return new Region(request.tileX(), request.tileZ(), request.tileSize()); }
    }
}
