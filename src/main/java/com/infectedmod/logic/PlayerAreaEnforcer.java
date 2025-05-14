package com.infectedmod.logic;


import com.infectedmod.InfectedMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Objects;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = InfectedMod.MODID)
public class PlayerAreaEnforcer {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // only run on server, end‐phase ticks
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;

        ServerPlayer player = (ServerPlayer)event.player;
        UUID id = player.getUUID();

        // 1) find which game this player is in
        Game game = SessionManager.get().getGameOfPlayer(id);

        if (game == null || !game.isRunning()){
            System.out.println("Game is not running ");
            return;
        }

        // 2) get that session's map area
        MapManager.MapData map = game.getCurrentMap();
        if (map == null) return;


        BlockPos here = player.blockPosition();
        System.out.println("Game is running ");
        if (!inside(here, map.pos1, map.pos2)) {
            // teleport back to spawn
            BlockPos s = map.spawn;
            ServerGamePacketListenerImpl conn = player.connection;
            conn.teleport(
                    s.getX() + 0.5, s.getY() + 0.5, s.getZ() + 0.5,
                    player.getYRot(), player.getXRot()
            );
            player.sendSystemMessage(Component.literal(
                    "You left the playable area—returning you to spawn."
            ));
        }
    }

    private static boolean inside(BlockPos p, BlockPos a, BlockPos b) {
        int minX = Math.min(a.getX(), b.getX()), maxX = Math.max(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY()), maxY = Math.max(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ()), maxZ = Math.max(a.getZ(), b.getZ());
        return p.getX() >= minX && p.getX() <= maxX
                && p.getY() >= minY && p.getY() <= maxY
                && p.getZ() >= minZ && p.getZ() <= maxZ;
    }
}
