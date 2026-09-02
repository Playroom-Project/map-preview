package io.github.playroomproject.mappreview.minecraft.pregen;

import io.github.playroomproject.mappreview.core.api.ResourceId;
import io.github.playroomproject.mappreview.pregen.ChunkPos;
import io.github.playroomproject.mappreview.pregen.PregenBridge;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.util.Identifier;

/** Native FULL futures and save barriers. Only this bridge's own tickets are released. */
public final class NativePregenBridge implements PregenBridge {
    private static final ChunkTicketType<net.minecraft.util.math.ChunkPos> TICKET = ChunkTicketType.create(
            "map_preview", java.util.Comparator.comparingLong(net.minecraft.util.math.ChunkPos::toLong));
    private final MinecraftServer server;
    private final Set<Ticket> tickets = new HashSet<>();

    public NativePregenBridge(MinecraftServer server) { this.server = server; }
    @Override public void assertServerThread() {
        if (!server.isOnThread()) { throw new IllegalStateException("Map PreView pregeneration requires the server thread"); }
    }
    @Override public ChunkTask submit(ResourceId dimension, ChunkPos position) {
        assertServerThread();
        var world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, new Identifier(dimension.value())));
        if (world == null) { throw new IllegalArgumentException("The server has no dimension " + dimension); }
        var manager = world.getChunkManager();
        var nativePosition = new net.minecraft.util.math.ChunkPos(position.x(), position.z());
        manager.addTicket(TICKET, nativePosition, 0, nativePosition);
        var ticket = new Ticket(manager, nativePosition);
        tickets.add(ticket);
        try {
            ticket.completion = NativeChunkAccess.full(manager, position.x(), position.z()).thenApplyAsync(chunk -> {
                chunk.setNeedsSaving(true);
                // The native future does not identify whether data came from disk; do not claim it is new.
                return new ChunkResult(false);
            }, server);
            return ticket;
        } catch (RuntimeException exception) { ticket.close(); throw exception; }
    }
    @Override public CompletionStage<Void> flush() {
        assertServerThread();
        try {
            server.saveAll(true, true, true);
            return CompletableFuture.completedFuture(null);
        } catch (RuntimeException exception) { return CompletableFuture.failedFuture(exception); }
    }
    public void releaseTickets() {
        assertServerThread();
        for (var ticket : Set.copyOf(tickets)) { ticket.close(); }
    }

    private final class Ticket implements ChunkTask {
        private final ServerChunkManager manager;
        private final net.minecraft.util.math.ChunkPos position;
        private CompletableFuture<ChunkResult> completion;
        private boolean closed;
        Ticket(ServerChunkManager manager, net.minecraft.util.math.ChunkPos position) { this.manager = manager; this.position = position; }
        @Override public CompletionStage<ChunkResult> completion() { return completion.minimalCompletionStage(); }
        @Override public void close() {
            assertServerThread();
            if (closed) { return; }
            closed = true;
            manager.removeTicket(TICKET, position, 0, position);
            tickets.remove(this);
        }
    }
}
