package com.infectedmod.logic;

import com.google.gson.*;
import net.minecraftforge.fml.loading.FMLPaths;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class PlayerStatsManager {
    private static final Path STATS_FILE =
            FMLPaths.CONFIGDIR.get().resolve("com/infectedmod/logic/player_stats.json");
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

    public PlayerStats getStats(UUID playerId) {
        return stats.computeIfAbsent(playerId, id -> new PlayerStats(0,0));
    }

    // helper for Game end‐of‐round or player logout
    public void updateStats(UUID id, int deltaPts, int deltaXp) {
        PlayerStats ps = getStats(id);
        ps.addPoints(deltaPts);
        ps.addXp(deltaXp);
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
