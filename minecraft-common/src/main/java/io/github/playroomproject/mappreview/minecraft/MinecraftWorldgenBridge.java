package io.github.playroomproject.mappreview.minecraft;

import java.util.concurrent.CompletionStage;

/** All mapping-sensitive Minecraft, registry and world-creation code lives behind this boundary. */
public interface MinecraftWorldgenBridge {
    /** Bootstrap detached preview state without mutating an active world or live mod configuration. */
    CompletionStage<? extends WorldCreationSnapshot> snapshot(WorldCreationInput input);
    /** Apply the selected state to the existing vanilla creation flow after the user's Create action. */
    void applyToVanillaCreation(WorldCreationSnapshot snapshot);
}
