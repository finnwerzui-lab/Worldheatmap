package com.theaterial.heatmap;

import net.fabricmc.loader.api.FabricLoader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public final class HeatmapExporter {
    private HeatmapExporter() {}

    /** Exportiert die gewaehlte Ebene als PNG. Gibt null zurueck, wenn keine Daten da sind. */
    public static Path export(HeatmapData data, String dim, int layer, String layerName) throws IOException {
        Map<Long, int[]> cells = data.cells(dim);
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        int max = 0;
        for (Map.Entry<Long, int[]> e : cells.entrySet()) {
            int v = e.getValue()[layer];
            if (v <= 0) continue;
            int x = HeatmapData.keyX(e.getKey()), z = HeatmapData.keyZ(e.getKey());
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
            max = Math.max(max, v);
        }
        if (max == 0) return null;

        int w = maxX - minX + 1, h = maxZ - minZ + 1;
        int scale = Math.max(1, Math.min(8, 4096 / Math.max(w, h)));
        BufferedImage img = new BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_ARGB);
        int bg = 0xFF101418;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) img.setRGB(x, y, bg);
        }
        for (Map.Entry<Long, int[]> e : cells.entrySet()) {
            int v = e.getValue()[layer];
            if (v <= 0) continue;
            int px = (HeatmapData.keyX(e.getKey()) - minX) * scale;
            int pz = (HeatmapData.keyZ(e.getKey()) - minZ) * scale;
            int col = HeatmapColors.color(v, max);
            for (int dz = 0; dz < scale; dz++) {
                for (int dx = 0; dx < scale; dx++) img.setRGB(px + dx, pz + dz, col);
            }
        }

        Path dir = FabricLoader.getInstance().getGameDir().resolve("screenshots");
        Files.createDirectories(dir);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path out = dir.resolve("heatmap_" + dim + "_" + layerName.toLowerCase() + "_" + stamp + ".png");
        ImageIO.write(img, "png", out.toFile());
        return out;
    }
}
