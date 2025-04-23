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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber(modid = InfectedMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ScoreboardManager {
    private static final String OBJ_NAME      = "InfectedHUD";
    private static final Component OBJ_TITLE = Component.literal("Infected Mod");

    private static Objective   sidebarObj;
    private static PlayerTeam  survivorsTeam;
    private static PlayerTeam  infectedTeam;

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
        survivorsTeam = sb.getPlayersTeam("Survivors");
        infectedTeam  = sb.getPlayersTeam("Infected");
        survivorsTeam.setColor(ChatFormatting.GREEN);
        infectedTeam.setColor(ChatFormatting.RED);
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

        // New HUD lines
        Map<String, Integer> lines = new LinkedHashMap<>();
        lines.put("Points: TBD", 5);
        lines.put("XP:     TBD", 4);

        if (game.isRunning()) {
            long ticksLeft = Math.max(0, Game.GAME_TICKS - game.getTickCounter());
            long sec = ticksLeft / 20;
            String time = String.format("%02d:%02d", sec/60, sec%60);

            lines.put("Survivors: " + game.getSurvivors().size(), 3);
            lines.put("Infected:  " + game.getInfected().size(), 2);
            lines.put("Time:      " + time,                      1);
        }

        for (var entry : lines.entrySet()) {
            sb.getOrCreatePlayerScore(ScoreHolder.forNameOnly(entry.getKey()), sidebarObj)
                    .set(entry.getValue());
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
