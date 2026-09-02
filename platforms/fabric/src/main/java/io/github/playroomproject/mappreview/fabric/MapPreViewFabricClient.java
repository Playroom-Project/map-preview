package io.github.playroomproject.mappreview.fabric;

import io.github.playroomproject.mappreview.core.MapPreView;
import io.github.playroomproject.mappreview.minecraft.client.MapPreViewScreen;
import io.github.playroomproject.mappreview.minecraft.client.PreviewSettings;
import io.github.playroomproject.mappreview.minecraft.client.PregenerationScreen;
import java.util.TreeMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

/** Fabric event wiring stays out of version-owned screens and world generation. */
public final class MapPreViewFabricClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        var loader = FabricLoader.getInstance();
        var settings = new PreviewSettings(loader.getConfigDir());
        settings.load();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client -> settings.close());
        var mods = new TreeMap<String, String>();
        loader.getAllMods().forEach(mod -> mods.put(mod.getMetadata().getId(), mod.getMetadata().getVersion().getFriendlyString()));
        String loaderVersion = mods.get("fabricloader");
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof CreateWorldScreen creation) {
                addPreviewButton(creation, width, height, button ->
                        client.setScreen(new MapPreViewScreen(creation, settings, loaderVersion, mods)));
            } else if (screen instanceof GameMenuScreen && client.getServer() != null && client.world != null && client.player != null) {
                var service = MapPreViewFabric.service(client.getServer());
                if (service != null) {
                    Screens.getButtons(screen).removeIf(button -> button.getMessage().getString().equals("Map PreView: Pregenerate"));
                    Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("Map PreView: Pregenerate"), button ->
                            client.setScreen(new PregenerationScreen(screen, service,
                                    client.world.getRegistryKey().getValue().toString(), client.player.getBlockX(), client.player.getBlockZ())))
                            .dimensions(width / 2 - 100, 4, 200, 20).build());
                }
            }
        });
    }

    private static void addPreviewButton(CreateWorldScreen screen, int width, int height, ButtonWidget.PressAction open) {
        var buttons = Screens.getButtons(screen);
        buttons.removeIf(button -> button.getMessage().getString().equals(MapPreView.NAME));
        var create = buttons.stream().filter(button -> hasKey(button, "selectWorld.create")).findFirst().orElse(null);
        var cancel = buttons.stream().filter(button -> hasKey(button, "gui.cancel")).findFirst().orElse(null);
        int controlWidth = Math.min(150, (width - 24) / 3);
        int left = (width - controlWidth) / 2;
        int y = height - 55;
        if (create != null && cancel != null) {
            // Keep all three actions in the native footer, leaving the tab navigation unobstructed.
            int rowLeft = (width - controlWidth * 3 - 8) / 2;
            create.setX(rowLeft);
            create.setWidth(controlWidth);
            cancel.setX(rowLeft + 2 * (controlWidth + 4));
            cancel.setWidth(controlWidth);
            left = rowLeft + controlWidth + 4;
            y = create.getY();
        }
        buttons.add(ButtonWidget.builder(Text.literal(MapPreView.NAME), open).dimensions(left, y, controlWidth, 20).build());
    }

    private static boolean hasKey(ClickableWidget widget, String key) {
        return widget.getMessage().getContent() instanceof TranslatableTextContent translated && translated.getKey().equals(key);
    }
}
