package io.github.playroomproject.mappreview.fabric;

import io.github.playroomproject.mappreview.config.AtomicJsonStore;
import io.github.playroomproject.mappreview.minecraft.pregen.NativePregenService;
import io.github.playroomproject.mappreview.pregen.ChunkPlan;
import io.github.playroomproject.mappreview.pregen.PregenState;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.WorldSavePath;

/** Runs the actual server lifecycle, FULL chunk futures, ticket release and durable checkpoints. */
public final class NativePregenGameTest implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 1200)
    public void pausesCancelsResumesAndSavesNativeChunks(TestContext context) throws Exception {
        var server = context.getWorld().getServer();
        var service = MapPreViewFabric.service(server);
        context.assertTrue(service != null, "Fabric did not install its server lifecycle service");
        service.start(new NativePregenService.JobSpec(context.getWorld().getRegistryKey().getValue().toString(),
                "square", 1024, -1024, 32, List.of(), ChunkPlan.Traversal.SPIRAL, 2));
        context.runAtEveryTick(new Runnable() {
            private int phase;
            @Override public void run() {
                var progress = service.progress();
                context.assertTrue(progress == null || progress.state() != PregenState.FAILED,
                        "Native chunk generation failed: " + (progress == null ? "" : progress.lastFailure()));
                if (progress == null) { return; }
                context.assertTrue(progress.inFlight() <= 2, "The native adapter exceeded the configured in-flight budget");
                try {
                    if (phase == 0 && progress.completedChunks() > 0) { service.pause(); phase = 1; }
                    else if (phase == 1 && progress.state() == PregenState.PAUSED) {
                        context.assertTrue(progress.inFlight() == 0 && progress.completedChunks() < 25, "Pause did not drain native work");
                        service.cancel(); phase = 2;
                    } else if (phase == 2 && progress.state() == PregenState.CANCELLED) {
                        service.resume(); phase = 3;
                    } else if (phase == 3 && progress.state() == PregenState.COMPLETED) {
                        context.assertTrue(progress.completedChunks() == 25 && progress.inFlight() == 0, "Incorrect completion or leaked in-flight chunks");
                        var saved = new AtomicJsonStore().read(server.getSavePath(WorldSavePath.ROOT).resolve("map_preview/pregen.json"),
                                NativePregenService.SavedJob.class);
                        context.assertTrue(saved.checkpoint().completedChunks() == 25, "Checkpoint was written before native chunks completed");
                        phase = 4;
                        context.complete();
                    }
                } catch (java.io.IOException exception) { throw new IllegalStateException("Native checkpoint/resume failed", exception); }
            }
        });
    }
}
