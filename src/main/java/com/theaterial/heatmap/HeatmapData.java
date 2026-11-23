package com.theaterial.heatmap;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Zaehler pro 16x16-Zelle (Chunk) und Dimension. */
public class HeatmapData {
    public static final int TICKS = 0, DEATHS = 1, BROKEN = 2;

    private final Map<String, Map<Long, int[]>> dims = new TreeMap<>();
    private boolean dirty;

    public static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    public static int keyX(long k) {
        return (int) (k >> 32);
    }

    public static int keyZ(long k) {
        return (int) k;
    }

    public void add(String dim, int type, int blockX, int blockZ) {
        long k = key(blockX >> 4, blockZ >> 4);
        int[] c = dims.computeIfAbsent(dim, d -> new HashMap<>()).computeIfAbsent(k, x -> new int[3]);
        c[type]++;
        dirty = true;
    }

    public Map<Long, int[]> cells(String dim) {
        return dims.getOrDefault(dim, Collections.emptyMap());
    }

    public Set<String> dimensions() {
        return dims.keySet();
    }

    public int max(String dim, int type) {
        int m = 0;
        for (int[] v : cells(dim).values()) {
            if (v[type] > m) m = v[type];
        }
        return m;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void save(Path file) {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            JsonObject d = new JsonObject();
            for (Map.Entry<String, Map<Long, int[]>> e : dims.entrySet()) {
                JsonArray arr = new JsonArray();
                for (Map.Entry<Long, int[]> c : e.getValue().entrySet()) {
                    int[] v = c.getValue();
                    JsonArray row = new JsonArray();
                    row.add(keyX(c.getKey()));
                    row.add(keyZ(c.getKey()));
                    row.add(v[0]);
                    row.add(v[1]);
                    row.add(v[2]);
                    arr.add(row);
                }
                d.add(e.getKey(), arr);
            }
            root.add("dims", d);

            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, new Gson().toJson(root), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            dirty = false;
        } catch (IOException e) {
            WorldHeatmap.LOGGER.error("Heatmap konnte nicht gespeichert werden: {}", file, e);
        }
    }

    public void load(Path file) {
        dims.clear();
        dirty = false;
        if (!Files.exists(file)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject d = root.getAsJsonObject("dims");
            if (d == null) return;
            for (Map.Entry<String, JsonElement> e : d.entrySet()) {
                Map<Long, int[]> map = new HashMap<>();
                for (JsonElement el : e.getValue().getAsJsonArray()) {
                    JsonArray row = el.getAsJsonArray();
                    int[] v = new int[] {row.get(2).getAsInt(), row.get(3).getAsInt(), row.get(4).getAsInt()};
                    map.put(key(row.get(0).getAsInt(), row.get(1).getAsInt()), v);
                }
                dims.put(e.getKey(), map);
            }
        } catch (Exception e) {
            WorldHeatmap.LOGGER.error("Heatmap konnte nicht geladen werden: {}", file, e);
        }
    }
}
