// Game.java – Core game logic for Minecraft Infected Mod (Forge 1.21.5)
package com.infectedmod.logic;

import com.infectedmod.InfectedMod;
import com.infectedmod.ui.ScoreboardManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import java.util.*;

@Mod.EventBusSubscriber(modid = InfectedMod.MODID)
public class Game {
    private static final int INTERMISSION_TICKS    = 30 * 20;
    public static final int GAME_TICKS           = 10 * 60 * 20;
    private static final int SURVIVOR_POINT_INTERVAL = 60 * 20;
    private static final int SURVIVOR_POINT       = 25;
    private static final int SURVIVOR_XP          = 50;
    private static final int INFECT_POINT         = 25;
    private static final int INFECT_XP            = 100;
    private static final int BONUS_SURVIVOR_POINT = 100;
    private static final int BONUS_SURVIVOR_XP    = 500;
    private static final int BONUS_INFECT_POINT   = 50;
    private static final int BONUS_INFECT_XP      = 200;
    // inside your Game class, alongside INTERMISSION_TICKS, GAME_TICKS...
    private static final int POST_INTERMISSION_TICKS = 20 * 20;  // 20s
    private boolean postGamePhase = false;
    private MapManager.MapData nextMap = null;
    private static Game instance;
    private final Set<UUID> survivors = new HashSet<>();
    private final Set<UUID> infected  = new HashSet<>();
    private final Map<UUID, PlayerStats> stats = new HashMap<>();
    private MapManager.MapData currentMap;



    private int  tickCounter = 0;
    private boolean intermission = false;
    private boolean running      = false;
    Scoreboard sb;
    PlayerTeam sTeam;
    PlayerTeam iTeam;
    public boolean isRunning()        { return running; }
    public MapManager.MapData getCurrentMap()    { return currentMap; }
    private Game() { }
    public static Game get() {
        if (instance == null) {
            instance = new Game();
        }
        return instance;
    }
    public Set<UUID> getSurvivors() { return survivors; }
    public Set<UUID> getInfected() { return infected; }
    public void startIntermission(MinecraftServer server) {
        // Reset state

        BlockPos spawn = currentMap.spawn;
        double sx = spawn.getX() + 0.5;
        double sy = spawn.getY() + 0.5;
        double sz = spawn.getZ() + 0.5;

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            UUID id = p.getUUID();
            survivors.add(id);
            stats.putIfAbsent(id, new PlayerStats());

            // Use the connection.teleport(...) method:
            ServerGamePacketListenerImpl conn = p.connection;
            conn.teleport(sx, sy, sz, p.getYRot(), p.getXRot());
        }


        survivors.clear();
        infected.clear();
        stats.clear();
        sb     = server.overworld().getScoreboard();
        sTeam  = sb.getPlayersTeam("Survivors");
        iTeam  = sb.getPlayersTeam("Infected");
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
            int duration = game.postGamePhase
                    ? POST_INTERMISSION_TICKS
                    : INTERMISSION_TICKS;
            if (game.tickCounter >= duration) {
                if (game.postGamePhase) {
                    // switch out of post-game into a fresh round
                    game.postGamePhase = false;
                    game.startGame(server);
                } else {
                    // initial intermission → first game
                    game.startGame(server);
                }
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



        // Inside your server‐tick loop (e.g. onServerTick in Game.java):
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();

            // Fetch this player’s stats
            PlayerStatsManager.PlayerStats stats =
                    PlayerStatsManager.get().getStats(uuid);

            // Build the action‐bar component
            Component text = Component.literal(
                    "Points: " + stats.getPoints() +
                            "    XP: "     + stats.getXp()
            );

            // Send it as an action‐bar packet
            player.connection.send(
                    new ClientboundSetActionBarTextPacket(text)
            );
        }

    }


    public void startGame(MinecraftServer server) {
        // 1) Grab a random map (Optional<MapData> → handle empty)
        sb = server.overworld().getScoreboard();
        if (sTeam == null) {
            sTeam = sb.getPlayerTeam("Survivors");
            if (sTeam == null) sTeam = sb.addPlayerTeam("Survivors");
            sTeam.setColor(ChatFormatting.GREEN);
        }
        if (iTeam == null) {
            iTeam = sb.getPlayerTeam("Infected");
            if (iTeam == null) iTeam = sb.addPlayerTeam("Infected");
            iTeam.setColor(ChatFormatting.RED);
        }

        Optional<MapManager.MapData> optMap = MapManager.get().getRandomMap();
        if (nextMap != null) {
            currentMap = nextMap;
            nextMap = null;
        } else {
            Optional<MapManager.MapData> opt = MapManager.get().getRandomMap();
            if (opt.isEmpty()) {
                // …no maps defined…
                return;
            }
            currentMap = opt.get();
        }
        if (optMap.isEmpty()) {
            // no maps defined → notify everyone
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.connection.send(new ClientboundSystemChatPacket(
                        Component.literal("[InfectedMod] No maps defined! Use /addMap first."),
                        false
                ));
            }
            return;
        }
        currentMap = optMap.get();
        sb.getTeamNames().forEach(sb::removePlayerFromTeam);  // clear all
        for (UUID id : survivors) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) sb.addPlayerToTeam(p.getScoreboardName(), sTeam);
        }

        // 2) Reset state
        tickCounter  = 0;
        intermission = false;
        running      = true;
        survivors.clear();
        infected.clear();

        // 3) Teleport everyone to the map's spawn, init survivors and stats


        // 4) Pick and announce the first infected
        List<UUID> list = new ArrayList<>(survivors);
        if (!list.isEmpty()) {
            UUID firstId = list.get(new Random().nextInt(list.size()));
            survivors.remove(firstId);
            infected.add(firstId);

            ServerPlayer firstPlayer = server.getPlayerList().getPlayer(firstId);
            if (firstPlayer != null) {
                // Teleport them as well (optional, same spawn)


                // Broadcast the “first infected” message
                String msg = firstPlayer.getName().getString() + " is the first infected!";
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    p.connection.send(new ClientboundSystemChatPacket(
                            Component.literal(msg),
                            false
                    ));
                }
            }
        }

        // 5) Announce round start
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSystemChatPacket(
                    Component.literal("Round started! 10 minutes on the clock."),
                    false
            ));
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
        sb.removePlayerFromTeam(target.getScoreboardName());
        sb.addPlayerToTeam(target.getScoreboardName(), iTeam);

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
        ScoreboardManager.announceWinner(server, survivorsWin);

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

        intermission    = true;
        postGamePhase   = true;
        tickCounter     = 0;

        Optional<MapManager.MapData> optNext = MapManager.get().getRandomMap();
        if (optNext.isPresent()) {
            nextMap = optNext.get();
            String nm = nextMap.name;
            // Broadcast "Next map: nm" to everyone
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.connection.send(new ClientboundSystemChatPacket(
                        Component.literal("Next map ➜ " + nm),
                        false
                ));
            }
        } else {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.connection.send(new ClientboundSystemChatPacket(
                        Component.literal("No maps defined for next round!"),
                        false
                ));
            }
        }
        // Inform players
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSystemChatPacket(
                    Component.literal("Next round begins in 20 seconds!"),
                    false
            ));
        }
        PlayerStatsManager.get().save();
    }

    public static class PlayerStats {
        private int points = 0;
        private int xp     = 0;

        public void addPoints(int p) { points += p; }
        public void addXp(int x)     { xp     += x; }
        public int getPoints()       { return points; }
        public int getXp()           { return xp; }
    }

    public boolean isPostGamePhase() {
        return postGamePhase;
    }

    public MapManager.MapData getNextMap() {
        return nextMap;
    }

    public static Game getInstance() {
        return instance;
    }

    public Map<UUID, PlayerStats> getStats() {
        return stats;
    }

    public int getTickCounter() {
        return tickCounter;
    }

    public boolean isIntermission() {
        return intermission;
    }

    public Scoreboard getSb() {
        return sb;
    }

    public PlayerTeam getsTeam() {
        return sTeam;
    }

    public PlayerTeam getiTeam() {
        return iTeam;
    }
}
