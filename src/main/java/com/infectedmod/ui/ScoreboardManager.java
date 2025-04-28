package com.infectedmod.ui;

import com.infectedmod.InfectedMod;
import com.infectedmod.logic.Game;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.*;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber(modid = InfectedMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.DEDICATED_SERVER)
public class ScoreboardManager {
    private static final String OBJ_NAME      = "InfectedHUD";
    private static final Component OBJ_TITLE = Component.literal("Infected Mod");

    private static Objective   sidebarObj;
    private static PlayerTeam  survivorsTeam;
    private static PlayerTeam  infectedTeam;
    private static final Set<String> oldScoreKeys = new HashSet<>();
    // 1) On server start, create objective & teams
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent ev) {
        MinecraftServer server = ev.getServer();
        Scoreboard sb = server.overworld().getScoreboard();

        // Sidebar objective
        sidebarObj = sb.getObjective(OBJ_NAME);
        if (sidebarObj == null) {
            sidebarObj = sb.addObjective(
                    OBJ_NAME,
                    ObjectiveCriteria.DUMMY,
                    OBJ_TITLE,
                    ObjectiveCriteria.RenderType.INTEGER,
                    true,
                    null
            );
        }
        sb.setDisplayObjective(DisplaySlot.SIDEBAR, sidebarObj);

        // TAB teams

        survivorsTeam = sb.getPlayerTeam("Survivors");
        if (survivorsTeam == null) {
            survivorsTeam = sb.addPlayerTeam("Survivors");
        }
        survivorsTeam.setColor(ChatFormatting.GREEN);

        // Get or create the Infected team
        infectedTeam = sb.getPlayerTeam("Infected");
        if (infectedTeam == null) {
            infectedTeam = sb.addPlayerTeam("Infected");
        }
        infectedTeam.setColor(ChatFormatting.RED);
    }

    /** Assign this player to the Survivors team (lazy‐init teams). */
    public static void assignSurvivor(ServerPlayer player) {
        Scoreboard sb = getSb(player);
        initTeams(sb);
        removeFromAnyTeam(player, sb);
        sb.addPlayerToTeam(player.getScoreboardName(), survivorsTeam);
    }

    /** Assign this player to the Infected team (lazy‐init teams). */
    public static void assignInfected(ServerPlayer player) {
        Scoreboard sb = getSb(player);
        initTeams(sb);
        removeFromAnyTeam(player, sb);
        sb.addPlayerToTeam(player.getScoreboardName(), infectedTeam);
    }

    /** Get the overworld scoreboard from this player’s dimension (always OVERWORLD). */
    private static Scoreboard getSb(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        // NOTE: .overworld().getScoreboard() or player.level().getScoreboard()
        return server.overworld().getScoreboard();
    }

    /** Ensure teams exist and are colored. */
    private static void initTeams(Scoreboard sb) {
        if (survivorsTeam == null) {
            survivorsTeam = sb.getPlayerTeam("Survivors");
            if (survivorsTeam == null) survivorsTeam = sb.addPlayerTeam("Survivors");
            survivorsTeam.setColor(ChatFormatting.GREEN);
        }
        if (infectedTeam == null) {
            infectedTeam = sb.getPlayerTeam("Infected");
            if (infectedTeam == null) infectedTeam = sb.addPlayerTeam("Infected");
            infectedTeam.setColor(ChatFormatting.RED);
        }
    }

    /** Safely remove the player from whichever team they’re currently on (if any). */
    private static void removeFromAnyTeam(ServerPlayer player, Scoreboard sb) {
        String name = player.getScoreboardName();
        PlayerTeam current = sb.getPlayersTeam(name);
        if (current != null) {
            // The signature is removePlayerFromTeam(String, PlayerTeam)
            sb.removePlayerFromTeam(name, current);
        }
    }
    // 2) Each server tick, update sidebar lines & reassign teams
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ev.getServer();
        Game game = Game.get();

        Scoreboard sb = server.overworld().getScoreboard();

        // Reassign teams
        sb.getTeamNames().forEach(name -> {
            PlayerTeam team = sb.getPlayersTeam(name);
            if (team != null) Objects.requireNonNull(sb.getPlayersTeam(name)).getPlayers().forEach(player -> sb.removePlayerFromTeam(player, team));
        });

        for (UUID id : game.getSurvivors()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) sb.addPlayerToTeam(p.getScoreboardName(), survivorsTeam);
        }

        for (UUID id : game.getInfected()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) sb.addPlayerToTeam(p.getScoreboardName(), infectedTeam);
        }



        for (String entry : sb.getObjectiveNames()) {
            sb.resetSinglePlayerScore(ScoreHolder.forNameOnly(entry), sidebarObj);

        }

//        }
        for (String key : oldScoreKeys) {
            sb.resetSinglePlayerScore(ScoreHolder.forNameOnly(key), sidebarObj);
        }
        oldScoreKeys.clear();

        Map<String,Integer> lines = new LinkedHashMap<>();
        lines.put("Survivors: " + game.getSurvivors().size(), 3);
        lines.put("Infected:  " + game.getInfected().size(), 2);
        long ticksLeft = Math.max(0, Game.GAME_TICKS - game.getTickCounter());
        String time = String.format("%02d:%02d", (ticksLeft/20)/60, (ticksLeft/20)%60);
        lines.put("Time:      " + time,                      1);

        for (var e : lines.entrySet()) {
            sb.getOrCreatePlayerScore(ScoreHolder.forNameOnly(e.getKey()), sidebarObj)
                    .set(e.getValue());
            oldScoreKeys.add(e.getKey());
        }
    }


    // 3) Call this from your Game.endGame(...) to flash the winner
    public static void announceWinner(MinecraftServer server, boolean survivorsWin) {
        String winner = survivorsWin ? "Survivors won!" : "Infected won!";
        ClientboundSetTitleTextPacket pkt =
                new ClientboundSetTitleTextPacket(Component.literal(winner));

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(pkt);
        }
    }
}
