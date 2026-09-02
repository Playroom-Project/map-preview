package io.github.playroomproject.mappreview.minecraft.client;

import io.github.playroomproject.mappreview.core.MapPreView;
import io.github.playroomproject.mappreview.core.api.ResourceId;
import io.github.playroomproject.mappreview.core.cache.Fingerprint;
import io.github.playroomproject.mappreview.core.color.BiomeColors;
import io.github.playroomproject.mappreview.minecraft.mixin.CreateWorldScreenInvoker;
import io.github.playroomproject.mappreview.minecraft.worldgen.NativeWorldSnapshot;
import io.github.playroomproject.mappreview.minecraft.worldgen.NativeDimensions;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.world.gen.GeneratorOptions;

/** Editor layered over vanilla Create World, using ordinary native controls. */
public final class MapPreViewScreen extends Screen {
    private final CreateWorldScreen parent;
    private final PreviewSettings settings;
    private final String loaderVersion;
    private final Map<String, String> mods;
    private final Fingerprint registryEpoch = Fingerprint.builder().add(UUID.randomUUID().toString()).finish();
    private ThreadPoolExecutor bootstrap;
    private PreviewCanvas canvas;
    private NativeWorldSnapshot snapshot;
    private TextFieldWidget seed;
    private TextFieldWidget slice;
    private TextFieldWidget biome;
    private TextFieldWidget hex;
    private ButtonWidget dimensions;
    private ButtonWidget layers;
    private ButtonWidget structures;
    private ButtonWidget create;
    private int selectedDimension;
    private long requestedAt;
    private long revision;
    private String seedText;
    private String status = "Preparing world preview...";
    private boolean removed;

    public MapPreViewScreen(CreateWorldScreen parent, PreviewSettings settings, String loaderVersion, Map<String, String> mods) {
        super(Text.literal(MapPreView.NAME));
        this.parent = parent;
        this.settings = settings;
        this.loaderVersion = loaderVersion;
        this.mods = Map.copyOf(mods);
        String value = parent.getWorldCreator().getSeed();
        seedText = value.isBlank() ? Long.toString(parent.getWorldCreator().getGeneratorOptionsHolder().generatorOptions().getSeed()) : value;
    }

    @Override protected void init() {
        removed = false;
        String previousFilter = biome == null ? "" : biome.getText();
        String previousColor = hex == null ? "#7FBF55" : hex.getText();
        if (bootstrap == null || bootstrap.isShutdown()) {
            bootstrap = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1), task -> {
                Thread thread = new Thread(task, "Map PreView snapshot"); thread.setDaemon(true); return thread;
            });
        }
        if (canvas == null) { canvas = new PreviewCanvas(settings.value()); }
        canvas.setBounds(10, 83, width - 20, height - 159);
        seed = addDrawableChild(new TextFieldWidget(textRenderer, 42, 27, Math.max(60, width - 202), 20, Text.literal("Seed")));
        seed.setMaxLength(128);
        seed.setText(seedText);
        seed.setChangedListener(text -> {
            seedText = text;
            requestedAt = System.nanoTime() + 300_000_000L;
            revision++;
            canvas.cancelSession();
            if (snapshot != null) { snapshot.close(); snapshot = null; }
            dimensions.active = false;
            create.active = false;
            status = "Updating seed...";
        });
        addDrawableChild(ButtonWidget.builder(Text.literal("Random"), button -> seed.setText(Long.toString(
                net.minecraft.util.math.random.Random.create().nextLong()))).dimensions(width - 153, 27, 72, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset map"), button -> canvas.reset()).dimensions(width - 77, 27, 67, 20).build());
        dimensions = addDrawableChild(ButtonWidget.builder(Text.literal("Dimension"), button -> {
            if (snapshot == null) { return; }
            selectedDimension = (selectedDimension + 1) % snapshot.dimensions().size();
            activateDimension();
        }).dimensions(10, 54, Math.max(100, width / 2 - 15), 20).build());
        layers = addDrawableChild(ButtonWidget.builder(Text.literal(canvas.view().label), button -> {
            canvas.cycleView(); button.setMessage(Text.literal(canvas.view().label)); updateTooltip();
        }).dimensions(width / 2, 54, Math.max(80, width / 2 - 79), 20).build());
        slice = addDrawableChild(new TextFieldWidget(textRenderer, width - 64, 54, 54, 20, Text.literal("Y slice")));
        slice.setMaxLength(8);
        slice.setText(Integer.toString(canvas.y()));
        slice.setTooltip(Tooltip.of(Text.literal("Biome and density sample Y coordinate")));
        slice.setChangedListener(value -> {
            try { canvas.setY(Integer.parseInt(value)); } catch (NumberFormatException ignored) { }
            updateTooltip();
        });
        biome = addDrawableChild(new TextFieldWidget(textRenderer, 10, height - 61, Math.max(70, width / 2 - 92), 18, Text.literal("Biome ID / filter")));
        biome.setMaxLength(256);
        biome.setSuggestion("minecraft:plains");
        biome.setChangedListener(value -> { biome.setSuggestion(value.isEmpty() ? "minecraft:plains" : ""); canvas.setBiomeFilter(value); });
        biome.setText(previousFilter);
        hex = addDrawableChild(new TextFieldWidget(textRenderer, Math.max(85, width / 2 - 76), height - 61, 66, 18, Text.literal("Biome color")));
        hex.setMaxLength(7);
        hex.setText(previousColor);
        addDrawableChild(ButtonWidget.builder(Text.literal("Set color"), button -> saveColor()).dimensions(width / 2, height - 61, 68, 18).build());
        structures = addDrawableChild(ButtonWidget.builder(Text.literal(canvas.structuresVisible() ? "Candidates: on" : "Structures: off"), button -> {
            canvas.toggleStructures(); button.setMessage(Text.literal(canvas.structuresVisible() ? "Candidates: on" : "Structures: off"));
        }).dimensions(width / 2 + 73, height - 61, Math.max(70, width / 2 - 83), 18).build());
        structures.setTooltip(Tooltip.of(Text.literal("Yellow markers are possible structure placements. Terrain suitability is not verified.")));
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> close()).dimensions(10, height - 28, 72, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Use seed"), button -> {
            parent.getWorldCreator().setSeed(seedText); client.setScreen(parent);
        }).dimensions(87, height - 28, 82, 20).build());
        create = addDrawableChild(ButtonWidget.builder(Text.literal("Create world"), button -> {
            parent.getWorldCreator().setSeed(seedText);
            client.setScreen(parent);
            ((CreateWorldScreenInvoker) parent).mapPreview$createLevel();
        }).dimensions(width - 112, height - 28, 102, 20).build());
        create.active = snapshot != null;
        dimensions.active = snapshot != null;
        if (snapshot == null) { requestedAt = System.nanoTime(); }
        else {
            dimensions.setMessage(Text.literal(snapshot.dimensions().get(selectedDimension).dimension().id().value()));
            dimensions.setTooltip(Tooltip.of(dimensions.getMessage()));
            updateTooltip();
        }
    }

    @Override public void tick() {
        if (requestedAt != 0 && System.nanoTime() >= requestedAt) { requestedAt = 0; requestSnapshot(); }
        canvas.tick();
    }

    private void requestSnapshot() {
        long generation = revision;
        long selectedSeed = GeneratorOptions.parseSeed(seedText).orElseGet(() -> net.minecraft.util.math.random.Random.create().nextLong());
        if (seedText.isBlank()) { seedText = Long.toString(selectedSeed); seed.setText(seedText); requestedAt = 0; generation = revision; }
        final long ticket = generation;
        var holder = parent.getWorldCreator().getGeneratorOptionsHolder();
        var dimensionOptions = NativeDimensions.copyOf(holder.selectedDimensions().dimensions());
        var registries = holder.getCombinedRegistryManager();
        var packs = holder.dataConfiguration().dataPacks().getEnabled();
        status = "Preparing world preview...";
        bootstrap.getQueue().clear();
        bootstrap.execute(() -> {
            try {
                var next = new NativeWorldSnapshot(registries, dimensionOptions, selectedSeed, "fabric", loaderVersion,
                        packs, mods, registryEpoch, holder.generatorOptions().shouldGenerateStructures());
                client.execute(() -> {
                    if (removed || ticket != revision) { next.close(); return; }
                    var previous = snapshot;
                    snapshot = next;
                    selectedDimension = Math.min(selectedDimension, snapshot.dimensions().size() - 1);
                    activateDimension();
                    if (previous != null) { previous.close(); }
                    status = "Drag to pan  |  Scroll to zoom  |  Filter or recolor a biome below";
                    create.active = true;
                    dimensions.active = true;
                });
            } catch (RuntimeException exception) {
                MapPreView.LOGGER.log(System.Logger.Level.WARNING, "Map PreView could not prepare the selected generator", exception);
                client.execute(() -> {
                    if (!removed && ticket == revision) { status = "Preview unavailable: " + exception.getMessage(); create.active = false; }
                });
            }
        });
    }

    private void activateDimension() {
        var context = snapshot.dimensions().get(selectedDimension);
        canvas.activate(context, snapshot.backend(context.dimension().id()));
        dimensions.setMessage(Text.literal(context.dimension().id().value()));
        dimensions.setTooltip(Tooltip.of(dimensions.getMessage()));
        layers.setMessage(Text.literal(canvas.view().label));
        slice.setText(Integer.toString(canvas.y()));
        updateTooltip();
    }

    private void updateTooltip() {
        if (canvas == null) { return; }
        layers.setTooltip(Tooltip.of(Text.literal(switch (canvas.view()) {
            case BIOMES, CAVE_BIOMES -> "Biome slice sampled at Y=" + canvas.y();
            case DENSITY -> "Raw density at Y=" + canvas.y() + "; aquifers and carvers are not included";
            case SLIME -> "Vanilla slime chunk seed rule. Other spawning conditions still apply.";
            default -> "Raw generator terrain before surface decoration and placed features";
        })));
    }

    private void saveColor() {
        try {
            var id = new ResourceId(biome.getText());
            BiomeColors.parseHex(hex.getText());
            if (canvas.context() == null) { return; }
            canvas.context().biomes().localId(id);
            var overrides = new HashMap<>(settings.value().biomeColors());
            overrides.put(id.value(), hex.getText());
            canvas.setColors(overrides, settings.value().biomeTagColors());
            settings.colors(overrides).whenComplete((ignored, exception) -> {
                if (exception != null) { client.execute(() -> status = "Could not save biome colors: " + exception.getMessage()); }
            });
            status = "Color updated for " + id.value();
        } catch (IllegalArgumentException exception) { status = "Enter a registered biome ID and a #RRGGBB color"; }
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xff0b1118);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xffe9f2ee);
        context.drawTextWithShadow(textRenderer, "Seed", 10, 33, 0xffcbd5d1);
        canvas.render(context);
        String hovered = canvas.hover(mouseX, mouseY);
        String info = hovered.isEmpty() ? status : hovered;
        if (!canvas.failure().isEmpty()) { info = "Preview error: " + canvas.failure(); }
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(info, width - 20), 10, height - 73, 0xffb8cfcc);
        var stats = canvas.stats();
        String progress = canvas.visibleTiles() + " tiles  |  " + stats.outstanding() + " pending";
        context.drawTextWithShadow(textRenderer, progress, 10, height - 40, 0xff819b98);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && canvas.contains(x, y)) { canvas.pan(dx, dy); return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    public boolean mouseScrolled(double x, double y, double amount) {
        if (canvas.contains(x, y)) { canvas.zoom(x, y, amount); return true; }
        return false;
    }
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) { return mouseScrolled(x, y, vertical); }
    @Override public boolean shouldPause() { return false; }
    /** Newer screens call this from super.render; this screen already paints its own background. */
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }
    @Override public void close() { client.setScreen(parent); }
    @Override public void removed() {
        removed = true;
        revision++;
        if (canvas != null) { canvas.close(); canvas = null; }
        if (snapshot != null) { snapshot.close(); snapshot = null; }
        if (bootstrap != null) { bootstrap.shutdownNow(); }
    }
}
