package io.github.playroomproject.mappreview.minecraft.mixin;

import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The only private client boundary: delegate creation to vanilla after applying the selected seed. */
@Mixin(CreateWorldScreen.class)
public interface CreateWorldScreenInvoker {
    @Invoker("createLevel") void mapPreview$createLevel();
}
