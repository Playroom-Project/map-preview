package io.github.playroomproject.mappreview.minecraft.client;

import io.github.playroomproject.mappreview.core.MapPreView;
import io.github.playroomproject.mappreview.minecraft.pregen.NativePregenService;
import io.github.playroomproject.mappreview.pregen.ChunkPlan;
import io.github.playroomproject.mappreview.pregen.ChunkPos;
import io.github.playroomproject.mappreview.pregen.PregenState;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Integrated-server controls. Every operation that touches a world is submitted to its server thread. */
public final class PregenerationScreen extends Screen {
    private final Screen parent;
    private final NativePregenService service;
    private final List<String> dimensions = new ArrayList<>();
    private final int initialX;
    private final int initialZ;
    private TextFieldWidget centerX;
    private TextFieldWidget centerZ;
    private TextFieldWidget radius;
    private TextFieldWidget polygon;
    private TextFieldWidget workers;
    private ButtonWidget start;
    private ButtonWidget pause;
    private ButtonWidget resume;
    private ButtonWidget cancel;
    private ButtonWidget dimension;
    private int dimensionIndex;
    private int shape;
    private ChunkPlan.Traversal traversal = ChunkPlan.Traversal.SPIRAL;
    private String feedback = "Pregeneration uses the server's normal chunk generator and saving.";
    private boolean submitting;

    public PregenerationScreen(Screen parent, NativePregenService service, String dimension, int x, int z) {
        super(Text.literal(MapPreView.NAME + " - Pregeneration"));
        this.parent = parent;
        this.service = service;
        initialX = x;
        initialZ = z;
        dimensions.add(dimension);
    }

    @Override protected void init() {
        int left = width / 2 - 150;
        dimension = addDrawableChild(ButtonWidget.builder(Text.literal(dimensions.get(dimensionIndex)), button -> {
            dimensionIndex = (dimensionIndex + 1) % dimensions.size();
            button.setMessage(Text.literal(dimensions.get(dimensionIndex)));
        }).dimensions(left, 33, 300, 20).build());
        service.server().execute(() -> {
            var available = new ArrayList<String>();
            service.server().getWorlds().forEach(world -> available.add(world.getRegistryKey().getValue().toString()));
            available.sort(String::compareTo);
            client.execute(() -> {
                if (client.currentScreen != this || available.isEmpty()) { return; }
                String selected = dimensions.get(dimensionIndex);
                dimensions.clear(); dimensions.addAll(available);
                dimensionIndex = Math.max(0, dimensions.indexOf(selected));
                dimension.setMessage(Text.literal(dimensions.get(dimensionIndex)));
            });
        });
        centerX = field(left, 65, 96, centerX == null ? Integer.toString(initialX) : centerX.getText(), "Center X (blocks)");
        centerZ = field(left + 102, 65, 96, centerZ == null ? Integer.toString(initialZ) : centerZ.getText(), "Center Z (blocks)");
        radius = field(left + 204, 65, 96, radius == null ? "256" : radius.getText(), "Radius (blocks)");
        addDrawableChild(ButtonWidget.builder(Text.literal(shapeName()), button -> {
            shape = (shape + 1) % 3; button.setMessage(Text.literal(shapeName()));
            updateFields();
        }).dimensions(left, 90, 96, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(traversal.name()), button -> {
            traversal = traversal == ChunkPlan.Traversal.SPIRAL ? ChunkPlan.Traversal.ROW_MAJOR : ChunkPlan.Traversal.SPIRAL;
            button.setMessage(Text.literal(traversal.name()));
        }).dimensions(left + 102, 90, 96, 20).build());
        workers = field(left + 204, 90, 96, workers == null ? "4" : workers.getText(), "Maximum chunks in flight (1-64)");
        polygon = field(left, 124, 300, polygon == null ? "0,0;256,0;256,256;0,256" : polygon.getText(), "Polygon vertices in block coordinates: x,z;x,z;x,z");
        polygon.setMaxLength(32768);
        start = addDrawableChild(ButtonWidget.builder(Text.literal("Start"), button -> startJob()).dimensions(left, 149, 71, 20).build());
        pause = addDrawableChild(ButtonWidget.builder(Text.literal("Pause"), button -> submit(service::pause)).dimensions(left + 76, 149, 71, 20).build());
        resume = addDrawableChild(ButtonWidget.builder(Text.literal("Resume"), button -> submit(service::resume)).dimensions(left + 152, 149, 71, 20).build());
        cancel = addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> submit(service::cancel)).dimensions(left + 228, 149, 72, 20).build());
        cancel.setTooltip(Tooltip.of(Text.literal("Stops after submitted chunks drain and saves a resumable checkpoint.")));
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> close()).dimensions(width / 2 - 45, height - 27, 90, 20).build());
        updateFields();
    }

    private TextFieldWidget field(int x, int y, int w, String value, String description) {
        var field = addDrawableChild(new TextFieldWidget(textRenderer, x, y, w, 20, Text.literal(description)));
        field.setMaxLength(32768);
        field.setText(value);
        field.setTooltip(Tooltip.of(Text.literal(description)));
        return field;
    }
    private String shapeName() { return List.of("Square", "Circle", "Polygon").get(shape); }
    private void updateFields() {
        polygon.setEditable(shape == 2);
        centerX.setEditable(shape != 2);
        centerZ.setEditable(shape != 2);
        radius.setEditable(shape != 2);
    }
    private void startJob() {
        try {
            var vertices = new ArrayList<ChunkPos>();
            if (shape == 2) {
                for (String pair : polygon.getText().split(";")) {
                    String[] coordinates = pair.trim().split(",");
                    if (coordinates.length != 2) { throw new IllegalArgumentException("Use x,z;x,z;x,z polygon coordinates"); }
                    vertices.add(new ChunkPos(Math.floorDiv(Integer.parseInt(coordinates[0].trim()), 16),
                            Math.floorDiv(Integer.parseInt(coordinates[1].trim()), 16)));
                }
            }
            var job = new NativePregenService.JobSpec(dimensions.get(dimensionIndex), shapeName().toLowerCase(Locale.ROOT),
                    shape == 2 ? 0 : Integer.parseInt(centerX.getText()), shape == 2 ? 0 : Integer.parseInt(centerZ.getText()),
                    shape == 2 ? 0 : Integer.parseInt(radius.getText()),
                    vertices, traversal, Integer.parseInt(workers.getText()));
            submit(() -> service.start(job));
        } catch (IllegalArgumentException exception) { feedback = "Invalid area: " + exception.getMessage(); }
    }
    private void submit(ServerAction action) {
        if (submitting) { return; }
        submitting = true;
        service.server().execute(() -> {
            String result;
            try { action.run(); result = service.message(); }
            catch (Exception exception) { result = "Pregeneration: " + exception.getMessage(); }
            String response = result;
            client.execute(() -> { feedback = response; submitting = false; });
        });
    }
    @Override public void tick() {
        var progress = service.progress();
        boolean idle = progress == null || (progress.inFlight() == 0 && switch (progress.state()) {
            case CANCELLED, COMPLETED, FAILED -> true;
            default -> false;
        });
        start.active = !submitting && idle;
        pause.active = !submitting && progress != null && progress.state() == PregenState.RUNNING;
        resume.active = !submitting && (idle || progress.state() == PregenState.PAUSED);
        cancel.active = !submitting && progress != null && !idle;
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xff0b1118);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xffe9f2ee);
        int left = width / 2 - 150;
        context.drawTextWithShadow(textRenderer, "Center X", left, 55, 0xffb8cfcc);
        context.drawTextWithShadow(textRenderer, "Center Z", left + 102, 55, 0xffb8cfcc);
        context.drawTextWithShadow(textRenderer, "Radius", left + 204, 55, 0xffb8cfcc);
        context.drawTextWithShadow(textRenderer, "Polygon vertices in blocks", left, 114, 0xffb8cfcc);
        var progress = service.progress();
        if (progress != null) {
            context.fill(left, 175, left + 300, 181, 0xff233c40);
            context.fill(left, 175, left + (int) (300 * Math.min(1, progress.fraction())), 181, 0xff77c79a);
            String details = String.format(Locale.ROOT, "%s: %,d / %,d | %.1f chunks/s | %d in flight",
                    progress.state(), progress.completedChunks(), progress.totalChunks(), progress.currentChunksPerSecond(), progress.inFlight());
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(details, width - 20)), width / 2, 185, 0xffd7e9e2);
        }
        String status = feedback.startsWith("Invalid") || feedback.startsWith("Pregeneration:") ? feedback : service.message();
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(status, width - 20)), width / 2, height - 41, 0xffb8cfcc);
        super.render(context, mouseX, mouseY, delta);
    }
    @Override public boolean shouldPause() { return false; }
    /** Newer screens call this from super.render; keep the progress text and panel sharp. */
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }
    @Override public void close() { client.setScreen(parent); }
    @FunctionalInterface private interface ServerAction { void run() throws Exception; }
}
