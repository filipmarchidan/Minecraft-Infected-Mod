package com.infectedmod.logic;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraftforge.fml.loading.FMLPaths;


import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class MapManager {
    private static final Path MAPS_FILE = FMLPaths.CONFIGDIR.get().resolve("com/infectedmod/logic/maps.json");
    private static MapManager instance;
    private final Map<String, MapData> maps = new LinkedHashMap<>();
    private final Random random = new Random();

    private MapManager() {
        loadMaps();
    }

    public static MapManager get() {
        if (instance == null) instance = new MapManager();
        return instance;
    }

    private void loadMaps() {
        try {
            if (!Files.exists(MAPS_FILE)) {
                Files.createDirectories(MAPS_FILE.getParent());
                saveMaps();
                return;
            }
            String json = Files.readString(MAPS_FILE);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("maps");
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                String name = o.get("name").getAsString();
                JsonObject pa = o.getAsJsonObject("playableArea");
                BlockPos p1 = parsePos(pa.getAsJsonObject("pos1"));
                BlockPos p2 = parsePos(pa.getAsJsonObject("pos2"));
                BlockPos spawn = parsePos(o.getAsJsonObject("spawnPoint"));
                maps.put(name, new MapData(name, p1, p2, spawn));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveMaps() {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (MapData md : maps.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", md.name);
            JsonObject pa = new JsonObject();
            pa.add("pos1", posToJson(md.pos1));
            pa.add("pos2", posToJson(md.pos2));
            o.add("playableArea", pa);
            o.add("spawnPoint", posToJson(md.spawn));
            arr.add(o);
        }
        root.add("maps", arr);
        try (Writer w = new FileWriter(MAPS_FILE.toFile())) {
            new GsonBuilder().setPrettyPrinting().create().toJson(root, w);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addMap(String name) {
        if (!maps.containsKey(name)) {
            maps.put(name, new MapData(name, BlockPos.ZERO, BlockPos.ZERO, BlockPos.ZERO));
            saveMaps();
        }
    }

    public void setPlayableArea(String name, BlockPos p1, BlockPos p2) {
        MapData md = maps.get(name);
        if (md != null) {
            md.pos1 = p1;
            md.pos2 = p2;
            saveMaps();
        }
    }

    public void setSpawnPoint(String name, BlockPos spawn) {
        MapData md = maps.get(name);
        if (md != null) {
            md.spawn = spawn;
            saveMaps();
        }
    }

    public Optional<MapData> getRandomMap() {
        if (maps.isEmpty()) return Optional.empty();
        List<MapData> list = new ArrayList<>(maps.values());
        return Optional.of(list.get(random.nextInt(list.size())));
    }

    private static BlockPos parsePos(JsonObject o) {
        int x = o.get("x").getAsInt();
        int y = o.get("y").getAsInt();
        int z = o.get("z").getAsInt();
        return new BlockPos(x, y, z);
    }

    private static JsonObject posToJson(BlockPos pos) {
        JsonObject o = new JsonObject();
        o.addProperty("x", pos.getX());
        o.addProperty("y", pos.getY());
        o.addProperty("z", pos.getZ());
        return o;
    }

    public static class MapData {
        public final String name;
        public BlockPos pos1;
        public BlockPos pos2;
        public BlockPos spawn;

        public MapData(String name, BlockPos pos1, BlockPos pos2, BlockPos spawn) {
            this.name = name;
            this.pos1 = pos1;
            this.pos2 = pos2;
            this.spawn = spawn;
        }

    }
}