// GameEventDispatcher.java
package com.infectedmod.logic;

import com.infectedmod.InfectedMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = InfectedMod.MODID,
        bus   = Mod.EventBusSubscriber.Bus.FORGE
)
public class GameDispatcher {
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        // Forward the tick to every live session’s Game instance:
        for (GameSession session : SessionManager.get().getSessions().values()) {
            Game game = SessionManager.get().getGame(session.getSessionId());
            if (game != null) {
                game.handleTick(ev);  // an instance method you define
            }
        }
    }
}
