package io.github.playroomproject.mappreview.minecraft.pregen;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.playroomproject.mappreview.pregen.ChunkPlan;
import io.github.playroomproject.mappreview.pregen.ChunkPos;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/** Permission-checked server controls, also available on a headless dedicated server. */
public final class PregenCommands {
    private PregenCommands() { }
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                  Function<MinecraftServer, NativePregenService> services) {
        var root = literal("map_preview").requires(source -> source.hasPermissionLevel(2));
        root.then(literal("status").executes(context -> execute(context.getSource(), services, service -> {
            var progress = service.progress();
            return progress == null ? service.message() : String.format(java.util.Locale.ROOT,
                    "%s: %,d / %,d chunks (%.1f%%), %.1f chunks/s, %d in flight. %s", progress.state(),
                    progress.completedChunks(), progress.totalChunks(), progress.fraction() * 100,
                    progress.currentChunksPerSecond(), progress.inFlight(), service.message());
        })));
        root.then(literal("pause").executes(context -> execute(context.getSource(), services, service -> { service.pause(); return service.message(); })));
        root.then(literal("resume").executes(context -> execute(context.getSource(), services, service -> { service.resume(); return service.message(); })));
        root.then(literal("cancel").executes(context -> execute(context.getSource(), services, service -> { service.cancel(); return service.message(); })));
        for (String shape : List.of("square", "circle")) {
            root.then(literal("start").then(literal(shape).then(argument("radius", IntegerArgumentType.integer(0, 1_000_000))
                    .executes(context -> execute(context.getSource(), services, service -> {
                        var source = context.getSource();
                        int radius = IntegerArgumentType.getInteger(context, "radius");
                        service.start(new NativePregenService.JobSpec(source.getWorld().getRegistryKey().getValue().toString(),
                                shape, (int) Math.floor(source.getPosition().x), (int) Math.floor(source.getPosition().z), radius,
                                List.of(), ChunkPlan.Traversal.SPIRAL, 4));
                        return "Started " + shape + " pregeneration with a " + radius + " block radius";
                    })))));
        }
        root.then(literal("polygon").then(argument("vertices", StringArgumentType.greedyString())
                .executes(context -> execute(context.getSource(), services, service -> {
                    var points = new ArrayList<ChunkPos>();
                    String[] coordinates = StringArgumentType.getString(context, "vertices").split(";");
                    if (coordinates.length < 3 || coordinates.length > 1024) { throw new IllegalArgumentException("Supply 3 to 1024 x,z block-coordinate pairs separated by semicolons"); }
                    for (String pair : coordinates) {
                        String[] parts = pair.trim().split(",");
                        if (parts.length != 2) { throw new IllegalArgumentException("Use x,z;x,z;x,z block coordinates"); }
                        points.add(new ChunkPos(Math.floorDiv(Integer.parseInt(parts[0].trim()), 16),
                                Math.floorDiv(Integer.parseInt(parts[1].trim()), 16)));
                    }
                    service.start(new NativePregenService.JobSpec(context.getSource().getWorld().getRegistryKey().getValue().toString(),
                            "polygon", 0, 0, 0, points, ChunkPlan.Traversal.SPIRAL, 4));
                    return "Started polygon pregeneration";
                }))));
        dispatcher.register(root);
    }

    private static int execute(ServerCommandSource source, Function<MinecraftServer, NativePregenService> services, Operation operation) {
        try {
            var service = services.apply(source.getServer());
            if (service == null) { throw new IllegalStateException("The server is still starting"); }
            String message = operation.run(service);
            source.sendFeedback(() -> Text.literal("Map PreView: " + message), false);
            return 1;
        } catch (java.io.IOException | RuntimeException exception) {
            source.sendError(Text.literal("Map PreView: " + exception.getMessage()));
            return 0;
        }
    }
    @FunctionalInterface private interface Operation { String run(NativePregenService service) throws java.io.IOException; }
}
