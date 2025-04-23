// Game.java – Core game logic for Minecraft Infected Mod (Forge 1.21.5)
package com.infectedmod.logic;

import com.infectedmod.InfectedMod;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber(modid = InfectedMod.MODID)
public class Game {
    private static final int INTERMISSION_TICKS    = 30 * 20;
    private static final int GAME_TICKS           = 10 * 60 * 20;
    private static final int SURVIVOR_POINT_INTERVAL = 60 * 20;
    private static final int SURVIVOR_POINT       = 25;
    private static final int SURVIVOR_XP          = 50;
    private static final int INFECT_POINT         = 25;
    private static final int INFECT_XP            = 100;
    private static final int BONUS_SURVIVOR_POINT = 100;
    private static final int BONUS_SURVIVOR_XP    = 500;
    private static final int BONUS_INFECT_POINT   = 50;
    private static final int BONUS_INFECT_XP      = 200;

    private static Game instance;
    private final Set<UUID> survivors = new HashSet<>();
    private final Set<UUID> infected  = new HashSet<>();
    private final Map<UUID, PlayerStats> stats = new HashMap<>();

    private int  tickCounter = 0;
    private boolean intermission = false;
    private boolean running      = false;

    private Game() { }

    public static Game get() {
        if (instance == null) {
            instance = new Game();
        }
        return instance;
    }

    public void startIntermission(MinecraftServer server) {
        // Reset state
        survivors.clear();
        infected.clear();
        stats.clear();

        PlayerList list = server.getPlayerList();
        // Add all online players as survivors
        list.getPlayers().forEach(p -> {
            survivors.add(p.getUUID());
            stats.put(p.getUUID(), new PlayerStats());
        });

        intermission = true;
        running      = false;
        tickCounter  = 0;

        // Broadcast to all players
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSystemChatPacket(Component.literal("Intermission started! Run and hide"), false));
        }

    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Game game = Game.get();
        MinecraftServer server = event.getServer();

        if (game.intermission) {
            game.tickCounter++;
            if (game.tickCounter >= INTERMISSION_TICKS) {
                game.startGame(server);
            }
        }
        else if (game.running) {
            game.tickCounter++;
            // Every minute, reward survivors
            if (game.tickCounter % SURVIVOR_POINT_INTERVAL == 0) {
                game.awardSurvivorRewards(server);
            }
            // Check end conditions
            if (game.tickCounter >= GAME_TICKS) {
                game.endGame(true, server);
            } else if (game.survivors.isEmpty()) {
                game.endGame(false, server);
            }
        }
    }

    private void startGame(MinecraftServer server) {
        intermission = false;
        running      = true;
        tickCounter  = 0;

        // Choose first infected
        List<UUID> list = new ArrayList<>(survivors);
        UUID first = list.get(new Random().nextInt(list.size()));
        survivors.remove(first);
        infected.add(first);
        stats.putIfAbsent(first, new PlayerStats());

        ServerPlayer sp = server.getPlayerList().getPlayer(first);
        if (sp != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(
                    Component.literal(sp.getName().getString() + " is the first infected!")
            );
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Game game = Game.get();
        if (!game.running) return;

        ServerPlayer player = (ServerPlayer) event.getEntity();
        UUID id = player.getUUID();
        if (game.survivors.contains(id)) {
            game.infectPlayer(player, null);
        }
    }

    // Call from collision listener or attack event
    public void onPlayerTouch(ServerPlayer target, ServerPlayer toucher) {
        if (!running) return;
        UUID tId  = target.getUUID();
        UUID byId = toucher.getUUID();
        if (survivors.contains(tId) && infected.contains(byId)) {
            infectPlayer(target, toucher);
        }
    }

    private void infectPlayer(ServerPlayer target, ServerPlayer by) {
        UUID id = target.getUUID();
        survivors.remove(id);
        infected.add(id);
        stats.putIfAbsent(id, new PlayerStats());

        if (by != null) {
            PlayerStats ps = stats.get(by.getUUID());
            ps.addPoints(INFECT_POINT);
            ps.addXp(INFECT_XP);
        }
        // TODO: teleport newly infected to map spawn
    }

    private void awardSurvivorRewards(MinecraftServer server) {
        for (UUID id : new HashSet<>(survivors)) {
            PlayerStats ps = stats.get(id);
            ps.addPoints(SURVIVOR_POINT);
            ps.addXp(SURVIVOR_XP);

            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            if (sp != null) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.sendSystemMessage(
                        Component.literal("Survivor reward: +" + SURVIVOR_POINT + " points, +" + SURVIVOR_XP + " XP")
                );}
            }
        }
    }

    private void endGame(boolean survivorsWin, MinecraftServer server) {
        running = false;
        String winner = survivorsWin ? "Survivors" : "Infected";

        // Announce winner
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSystemChatPacket(Component.literal(winner + " won!"), false));
        }

        Set<UUID> winners = survivorsWin ? survivors : infected;
        for (UUID id : winners) {
            PlayerStats ps = stats.get(id);
            ps.addPoints(survivorsWin ? BONUS_SURVIVOR_POINT : BONUS_INFECT_POINT);
            ps.addXp(survivorsWin ? BONUS_SURVIVOR_XP : BONUS_INFECT_XP);
        }
    }

    private static class PlayerStats {
        private int points = 0;
        private int xp     = 0;

        public void addPoints(int p) { points += p; }
        public void addXp(int x)     { xp     += x; }
        public int getPoints()       { return points; }
        public int getXp()           { return xp; }
    }
}
