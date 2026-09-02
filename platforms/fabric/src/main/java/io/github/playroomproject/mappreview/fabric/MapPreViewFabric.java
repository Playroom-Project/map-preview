package io.github.playroomproject.mappreview.fabric;

import io.github.playroomproject.mappreview.core.MapPreView;
import io.github.playroomproject.mappreview.minecraft.pregen.NativePregenService;
import io.github.playroomproject.mappreview.minecraft.pregen.PregenCommands;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;

/** Dedicated-server-safe bootstrap; client classes are loaded only by the client entrypoint. */
public final class MapPreViewFabric implements ModInitializer {
    private static final Map<MinecraftServer, NativePregenService> SERVICES = new ConcurrentHashMap<>();
    public static NativePregenService service(MinecraftServer server) { return SERVICES.get(server); }
    @Override public void onInitialize() {
        var mods = FabricLoader.getInstance().getAllMods().stream().collect(Collectors.toUnmodifiableMap(
                mod -> mod.getMetadata().getId(), mod -> mod.getMetadata().getVersion().getFriendlyString()));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> SERVICES.put(server, new NativePregenService(server, mods)));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var service = SERVICES.get(server); if (service != null) { service.tick(); }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            var service = SERVICES.get(server); if (service != null) { service.stop(); }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(SERVICES::remove);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var service = SERVICES.get(server); if (service != null) { service.onPlayerJoin(); }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> PregenCommands.register(dispatcher, SERVICES::get));
        MapPreView.LOGGER.log(System.Logger.Level.INFO, "Map PreView initialized");
    }
}
