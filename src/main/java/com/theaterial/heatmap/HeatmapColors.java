package com.theaterial.heatmap;

/** Farbskala Blau (wenig) -> Gruen -> Gelb -> Rot (viel), logarithmisch. */
public final class HeatmapColors {
    private HeatmapColors() {}

    public static int color(int value, int max) {
        if (max < 1) max = 1;
        double t = Math.log1p(value) / Math.log1p(max);
        return fromT((float) Math.min(1.0, Math.max(0.0, t)));
    }

    public static int fromT(float t) {
        float hue = (1f - t) * 240f; // 240 = Blau, 0 = Rot
        float h = hue / 60f;
        int i = (int) Math.floor(h) % 6;
        float f = h - (float) Math.floor(h);
        float q = 1f - f;
        float r, g, b;
        switch (i) {
            case 0 -> { r = 1; g = f; b = 0; }
            case 1 -> { r = q; g = 1; b = 0; }
            case 2 -> { r = 0; g = 1; b = f; }
            case 3 -> { r = 0; g = q; b = 1; }
            default -> { r = f; g = 0; b = 1; }
        }
        int ri = Math.round(r * 255), gi = Math.round(g * 255), bi = Math.round(b * 255);
        return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
    }
}
