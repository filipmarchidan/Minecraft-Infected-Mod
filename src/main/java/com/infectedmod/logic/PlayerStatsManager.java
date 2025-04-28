package com.infectedmod.logic;

import com.google.gson.*;
import com.infectedmod.InfectedMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class PlayerStatsManager {
    private static final Path STATS_FILE =
            FMLPaths.CONFIGDIR.get().resolve(InfectedMod.MODID).resolve("player_stats.json");
    private static PlayerStatsManager instance;
    private final Map<UUID, PlayerStats> stats = new HashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private PlayerStatsManager() { load(); }
    public static PlayerStatsManager get() {
        if (instance == null) instance = new PlayerStatsManager();
        return instance;
    }

    private void load() {
        try {
            if (!Files.exists(STATS_FILE)) {
                Files.createDirectories(STATS_FILE.getParent());
                save();  // writes an empty template
                return;
            }
            JsonObject root = JsonParser.parseString(Files.readString(STATS_FILE)).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("players");
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                UUID id = UUID.fromString(o.get("uuid").getAsString());
                int p   = o.get("points").getAsInt();
                int x   = o.get("xp").getAsInt();
                stats.put(id, new PlayerStats(p, x));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void save() {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (Map.Entry<UUID, PlayerStats> e : stats.entrySet()) {
            JsonObject o = new JsonObject();
            o.addProperty("uuid",   e.getKey().toString());
            o.addProperty("points", e.getValue().getPoints());
            o.addProperty("xp",     e.getValue().getXp());
            arr.add(o);
        }
        root.add("players", arr);
        try (Writer w = Files.newBufferedWriter(STATS_FILE)) {
            gson.toJson(root, w);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
        @SubscribeEvent
        public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent ev) {
            if (!(ev.getEntity() instanceof ServerPlayer player)) return;
            UUID id = player.getUUID();
            // computeIfAbsent → ensures stats exist
            var stats = PlayerStatsManager.get().getStats(id);
            if (stats.getPoints() == 0 && stats.getXp() == 0) {
                player.sendSystemMessage(
                        Component.literal("Welcome, new player! Your stats have been initialized.")
                );
            } else {
                player.sendSystemMessage(
                        Component.literal("Welcome back! Points: " + stats.getPoints() +
                                ", XP: " + stats.getXp())
                );
            }
            // immediately persist the file so you see it on disk
            PlayerStatsManager.get().save();
        }



    public PlayerStats getStats(UUID playerId) {
        return stats.computeIfAbsent(playerId, id -> new PlayerStats(0,0));
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent ev) {
        if (ev.getEntity() instanceof ServerPlayer player) {
            UUID id = player.getUUID();
            // any last‐minute stat updates…
            PlayerStatsManager.get().save();
        }
    }
    // helper for Game end‐of‐round or player logout
    public void updateStats(UUID id, int deltaPts, int deltaXp) {
        PlayerStats ps = getStats(id);
        ps.addPoints(deltaPts);
        ps.addXp(deltaXp);
        PlayerStatsManager.get().save();
    }

    public static class PlayerStats {
        private int points, xp;
        public PlayerStats(int p, int x) { points = p; xp = x; }
        public int getPoints() { return points; }
        public int getXp()     { return xp; }
        public void addPoints(int p) { points += p; }
        public void addXp(int x)     { xp     += x; }
    }
}
