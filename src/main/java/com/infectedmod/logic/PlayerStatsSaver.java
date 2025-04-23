package com.infectedmod.logic;

import com.infectedmod.InfectedMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = InfectedMod.MODID)
public class PlayerStatsSaver {
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID id = player.getUUID();

        // Persist this player’s accumulated stats
        PlayerStatsManager.get().save();
        // (Or implement a saveSingle(id) if you want to write only that entry.)
    }
}