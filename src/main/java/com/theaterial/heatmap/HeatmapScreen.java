package com.theaterial.heatmap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public class HeatmapScreen extends Screen {
    private static final String[] LAYER_NAMES = {"Aufenthalt", "Tode", "Abbau"};

    private final HeatmapData data = WorldHeatmap.data;
    private int layer = HeatmapData.TICKS;
    private String dim;
    private double centerX, centerZ; // in Chunk-Einheiten
    private double zoom = 4.0;       // Pixel pro Chunk
    private int max = 1;
    private int frame;
    private final Button[] layerButtons = new Button[3];
    private Button dimButton;

    public HeatmapScreen() {
        super(Component.literal("World Heatmap"));
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (dim == null && mc.level != null && mc.player != null) {
            dim = WorldHeatmap.dimKey(mc.level);
            centerX = mc.player.getBlockX() / 16.0;
            centerZ = mc.player.getBlockZ() / 16.0;
        }
        if (dim == null) dim = "overworld";

        int x = 6, y = 6, w = 96, h = 20, step = 22;
        for (int i = 0; i < 3; i++) {
            final int l = i;
            layerButtons[i] = addRenderableWidget(Button.builder(Component.literal(LAYER_NAMES[i]), b -> setLayer(l))
                    .bounds(x, y + i * step, w, h).build());
        }
        dimButton = addRenderableWidget(Button.builder(Component.literal(dimLabel()), b -> cycleDim())
                .bounds(x, y + 3 * step, w, h).build());
        addRenderableWidget(Button.builder(Component.literal("Export PNG"), b -> doExport())
                .bounds(x, y + 4 * step, w, h).build());
        addRenderableWidget(Button.builder(Component.literal("Zentrieren"), b -> center())
                .bounds(x, y + 5 * step, w, h).build());
        setLayer(layer);
    }

    private void setLayer(int l) {
        layer = l;
        for (int i = 0; i < 3; i++) {
            if (layerButtons[i] != null) layerButtons[i].active = i != layer;
        }
        recomputeMax();
    }

    private void recomputeMax() {
        max = Math.max(1, data.max(dim, layer));
    }

    private String dimLabel() {
        return switch (dim) {
            case "overworld" -> "Oberwelt";
            case "nether" -> "Nether";
            case "end" -> "End";
            default -> dim.length() > 14 ? dim.substring(0, 14) : dim;
        };
    }

    private void cycleDim() {
        TreeSet<String> all = new TreeSet<>(data.dimensions());
        all.add(dim);
        List<String> list = new ArrayList<>(all);
        dim = list.get((list.indexOf(dim) + 1) % list.size());
        dimButton.setMessage(Component.literal(dimLabel()));
        recomputeMax();
    }

    private void center() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            centerX = mc.player.getBlockX() / 16.0;
            centerZ = mc.player.getBlockZ() / 16.0;
        }
        zoom = 4.0;
    }

    private void doExport() {
        Minecraft mc = Minecraft.getInstance();
        String msg;
        try {
            Path p = HeatmapExporter.export(data, dim, layer, LAYER_NAMES[layer]);
            msg = p == null ? "Heatmap: Keine Daten zum Exportieren." : "Heatmap gespeichert: " + p.getFileName() + " (Ordner screenshots)";
        } catch (Exception e) {
            WorldHeatmap.LOGGER.error("Export fehlgeschlagen", e);
            msg = "Heatmap: Export fehlgeschlagen, siehe Log.";
        }
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(msg));
    }

    // Kein Standard-Hintergrund (Blur), damit die Karte sichtbar bleibt
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
    }

    private int sx(double chunk) {
        return (int) Math.floor(width / 2.0 + (chunk - centerX) * zoom);
    }

    private int sz(double chunk) {
        return (int) Math.floor(height / 2.0 + (chunk - centerZ) * zoom);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xFF101418);
        if (frame++ % 30 == 0) recomputeMax();

        // Achsen (Block 0)
        int ax = sx(0), az = sz(0);
        g.fill(ax, 0, ax + 1, height, 0x30FFFFFF);
        g.fill(0, az, width, az + 1, 0x30FFFFFF);

        for (Map.Entry<Long, int[]> e : data.cells(dim).entrySet()) {
            int v = e.getValue()[layer];
            if (v <= 0) continue;
            int kx = HeatmapData.keyX(e.getKey()), kz = HeatmapData.keyZ(e.getKey());
            int x0 = sx(kx), z0 = sz(kz);
            int x1 = Math.max(x0 + 1, sx(kx + 1)), z1 = Math.max(z0 + 1, sz(kz + 1));
            if (x1 < 0 || z1 < 0 || x0 > width || z0 > height) continue;
            g.fill(x0, z0, x1, z1, HeatmapColors.color(v, max));
        }

        // Spielermarker
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null && dim.equals(WorldHeatmap.dimKey(mc.level))) {
            int px = sx(mc.player.getX() / 16.0), pz = sz(mc.player.getZ() / 16.0);
            g.fill(px - 2, pz - 2, px + 3, pz + 3, 0xFF000000);
            g.fill(px - 1, pz - 1, px + 2, pz + 2, 0xFFFFFFFF);
        }

        super.extractRenderState(g, mx, my, pt); // Buttons

        // Titel
        String title = "World Heatmap  -  " + dimLabel() + "  -  " + LAYER_NAMES[layer];
        g.text(font, title, (width - font.width(title)) / 2, 8, 0xFFFFFFFF, true);

        // Legende
        int lx = 6, ly = height - 40;
        for (int i = 0; i < 120; i++) g.fill(lx + i, ly, lx + i + 1, ly + 8, HeatmapColors.fromT(i / 119f));
        g.text(font, "wenig", lx, ly + 10, 0xFFCCCCCC, true);
        String hi = "viel (" + fmtValue(max) + ")";
        g.text(font, hi, lx + 120 - font.width(hi), ly + 10, 0xFFCCCCCC, true);

        // Info zur Zelle unter der Maus
        int hcx = (int) Math.floor(centerX + (mx - width / 2.0) / zoom);
        int hcz = (int) Math.floor(centerZ + (my - height / 2.0) / zoom);
        int[] v = data.cells(dim).get(HeatmapData.key(hcx, hcz));
        String info = "Chunk " + hcx + ", " + hcz + "  (Block " + (hcx * 16) + ", " + (hcz * 16) + ")";
        if (v != null) info += "   |   Zeit: " + fmtTicks(v[0]) + "   Tode: " + v[1] + "   Abbau: " + v[2];
        int tw = font.width(info);
        int tx = (width - tw) / 2, ty = height - 16;
        g.fill(tx - 4, ty - 3, tx + tw + 4, ty + 11, 0xA0000000);
        g.text(font, info, tx, ty, 0xFFFFFFFF, true);
    }

    private String fmtValue(int v) {
        return layer == HeatmapData.TICKS ? fmtTicks(v) : String.valueOf(v);
    }

    private static String fmtTicks(int ticks) {
        long s = ticks / 20L;
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return h + "h " + m + "m";
        return m + "m " + sec + "s";
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        centerX -= dragX / zoom;
        centerZ -= dragY / zoom;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double old = zoom;
        zoom = Math.max(0.05, Math.min(64.0, zoom * Math.pow(1.2, scrollY)));
        double wx = centerX + (mouseX - width / 2.0) / old;
        double wz = centerZ + (mouseY - height / 2.0) / old;
        centerX = wx - (mouseX - width / 2.0) / zoom;
        centerZ = wz - (mouseY - height / 2.0) / zoom;
        return true;
    }
}
