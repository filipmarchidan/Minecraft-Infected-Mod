//package com.infectedmod.ui;
//
//import com.infectedmod.InfectedMod;
//import com.infectedmod.logic.PlayerStatsManager;
//import com.mojang.blaze3d.vertex.PoseStack;
//import net.minecraft.client.Minecraft;
//import net.minecraft.client.gui.GuiGraphics;
//import net.minecraft.network.chat.Component;
//import net.minecraftforge.api.distmarker.Dist;
//import net.minecraftforge.client.*;
//import net.minecraftforge.eventbus.api.SubscribeEvent;
//import net.minecraftforge.fml.common.Mod;
//
//@Mod.EventBusSubscriber(
//        modid = InfectedMod.MODID,
//        value = Dist.CLIENT,
//        bus   = Mod.EventBusSubscriber.Bus.FORGE
//)
//public class HudManager {
//    @SubscribeEvent
//    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
//        // You can check event.getOverlay() if you only
//        // want to draw once per frame (e.g. after HOTBAR)
//        Minecraft mc = Minecraft.getInstance();
//        if (mc.player == null) return;
//
//        // pull per‐player stats
//        var stats = PlayerStatsManager.get().getStats(mc.player.getUUID());
//        GuiGraphics gg = event.getGuiGraphics();
//        int x = 10, y = 10;
//
//        gg.drawString(mc.font,
//                Component.literal("Points: " + stats.getPoints()),
//                x,   y, 0xFFFFFF
//        );
//        gg.drawString(mc.font,
//                Component.literal("XP:     " + stats.getXp()),
//                x, y + 10, 0xFFFFFF
//        );
//    }
//}
