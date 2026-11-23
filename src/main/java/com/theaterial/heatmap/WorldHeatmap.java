package com.theaterial.heatmap;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class WorldHeatmap implements ClientModInitializer {
    public static final String MOD_ID = "worldheatmap";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static HeatmapData data = new HeatmapData();
    private static Path savePath;
    private static KeyMapping openKey;
    private static int saveTimer;
    private static boolean wasDead;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.worldheatmap.open", InputConstants.Type.KEYBOARD, InputConstants.KEY_K, category));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onJoin(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onLeave());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> onLeave());
        ClientTickEvents.END_CLIENT_TICK.register(WorldHeatmap::tick);

        // Abbau (clientseitig, funktioniert auch auf Servern)
        ClientPlayerBlockBreakEvents.AFTER.register((level, player, pos, state) -> {
            if (savePath != null) data.add(dimKey(level), HeatmapData.BROKEN, pos.getX(), pos.getZ());
        });
    }

    private static void onJoin(Minecraft client) {
        onLeave();
        data = new HeatmapData();
        wasDead = false;
        saveTimer = 0;
        savePath = resolveSavePath(client);
        data.load(savePath);
        LOGGER.info("Heatmap-Datei: {}", savePath);
    }

    private static void onLeave() {
        if (savePath != null && data.isDirty()) data.save(savePath);
        savePath = null;
    }

    private static Path resolveSavePath(Minecraft client) {
        var server = client.getSingleplayerServer();
        if (server != null) {
            return server.getWorldPath(LevelResource.ROOT).resolve("worldheatmap.json");
        }
        ServerData sd = client.getCurrentServer();
        String name = sd != null ? sd.ip : "unknown";
        return FabricLoader.getInstance().getConfigDir().resolve("worldheatmap")
                .resolve(name.replaceAll("[^a-zA-Z0-9._-]", "_") + ".json");
    }

    private static void tick(Minecraft mc) {
        while (openKey.consumeClick()) {
            if (mc.gui.screen() == null && mc.player != null && savePath != null) {
                mc.gui.setScreen(new HeatmapScreen());
            }
        }
        if (mc.player == null || mc.level == null || savePath == null || mc.isPaused()) return;

        Player p = mc.player;
        String dim = dimKey(mc.level);
        boolean dead = p.isDeadOrDying();
        if (dead && !wasDead) data.add(dim, HeatmapData.DEATHS, p.getBlockX(), p.getBlockZ());
        wasDead = dead;
        if (!dead) data.add(dim, HeatmapData.TICKS, p.getBlockX(), p.getBlockZ());

        if (++saveTimer >= 1200) { // alle 60 Sekunden
            saveTimer = 0;
            if (data.isDirty()) data.save(savePath);
        }
    }

    public static String dimKey(Level level) {
        var k = level.dimension();
        if (k == Level.OVERWORLD) return "overworld";
        if (k == Level.NETHER) return "nether";
        if (k == Level.END) return "end";
        String s = k.toString();
        int i = s.lastIndexOf("/ ");
        int j = s.lastIndexOf(']');
        if (i >= 0 && j > i) s = s.substring(i + 2, j);
        return s.replaceAll("[^a-zA-Z0-9_:./-]", "_");
    }
}
