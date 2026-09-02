package io.github.playroomproject.mappreview.core.cache;

import io.github.playroomproject.mappreview.core.MapPreView;
import io.github.playroomproject.mappreview.core.tile.RasterTile;
import io.github.playroomproject.mappreview.core.tile.TileKey;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Optional compressed persistence codec; decoding is bounded by the expected tile geometry. */
public final class RasterTileCodec {
    private static final int MAGIC = 0x4d505256;
    private RasterTileCodec() { }

    public static byte[] encode(RasterTile tile) throws IOException {
        var output = new ByteArrayOutputStream();
        try (var data = new DataOutputStream(new GZIPOutputStream(output))) {
            data.writeInt(MAGIC);
            data.writeInt(MapPreView.CACHE_FORMAT_VERSION);
            data.writeUTF(keyFingerprint(tile.key()).hex());
            data.writeInt(tile.size());
            for (int i = 0; i < tile.size(); i++) { data.writeInt(tile.value(i)); }
        }
        return output.toByteArray();
    }

    public static RasterTile decode(TileKey expected, byte[] compressed) throws IOException {
        if (expected.request().layer().structures()) { throw new IOException("A structure query is not a raster tile"); }
        int side = expected.request().samplesPerSide();
        int count = Math.multiplyExact(side, side);
        if (compressed.length > count * 4L + 4096) { throw new IOException("Compressed tile exceeds its input limit"); }
        try (var data = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(compressed)))) {
            if (data.readInt() != MAGIC || data.readInt() != MapPreView.CACHE_FORMAT_VERSION
                    || !data.readUTF().equals(keyFingerprint(expected).hex()) || data.readInt() != count) {
                throw new IOException("Map PreView cache identity or geometry mismatch");
            }
            var tile = RasterTile.builder(expected);
            for (int i = 0; i < count; i++) { tile.set(i, data.readInt()); }
            if (data.read() != -1) { throw new IOException("Unexpected trailing tile data"); }
            return tile.freeze();
        }
    }

    public static Fingerprint keyFingerprint(TileKey key) {
        var request = key.request();
        var hash = Fingerprint.builder().add("tile-key-v1").add(key.sessionFingerprint().hex())
                .add(request.tileX()).add(request.tileZ()).add(request.tileSize()).add(request.step())
                .add(request.layer().name()).add(request.y()).add(request.heightMode().name())
                .add(request.structures().maximumResults()).add(request.structures().ids().size());
        request.structures().ids().stream().sorted().forEach(id -> hash.add(id.value()));
        return hash.finish();
    }
}
