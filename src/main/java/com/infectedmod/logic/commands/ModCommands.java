package com.infectedmod.logic.commands;


import com.infectedmod.InfectedMod;
import com.infectedmod.logic.Game;
import com.infectedmod.logic.MapManager;
import com.infectedmod.logic.PlayerStatsManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

@Mod.EventBusSubscriber(
        modid = InfectedMod.MODID,
        bus   = Mod.EventBusSubscriber.Bus.FORGE   // <— ensure Forge bus, not the mod (lifecycle) bus
)
public class ModCommands {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(literal("startGame")
                .requires(source -> source.hasPermission(4))
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    MinecraftServer server = src.getServer();
                    Game.get().startIntermission(server);
                    src.sendSuccess(() -> Component.literal("Intermission started."), true);
                    return 1;
                })
        );


        dispatcher.register(literal("endGame")
                .requires(source -> source.hasPermission(4))
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    MinecraftServer server = src.getServer();
                    Game.get().stopGame(server);
                    src.sendSuccess(() -> Component.literal("The Game has been stopped"), true);
                    return 1;
                })
        );


        dispatcher.register(literal("nextMap")
                .requires(src -> src.hasPermission(2))
                .then(argument("name", StringArgumentType.word())
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            MapManager mm = MapManager.get();
                            Game game = Game.get();
                            if (mm.hasMap(name) && (game.isRunning() || game.isIntermission())) {
                                game.setNextMap(mm.getMap(name));
                                ctx.getSource().sendSuccess( () ->
                                                Component.literal("§aNext map was set to '" + name + "'."),
                                        false
                                );
                            } else {
                                ctx.getSource().sendFailure(
                                        Component.literal("§cMap '" + name + "' already exists.")
                                );

                            }
                            return 1;
                        })
                )
        );

        dispatcher.register(literal("addMap")
                .requires(src -> src.hasPermission(2))
                .then(argument("name", StringArgumentType.word())
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            MapManager mm = MapManager.get();
                            if (mm.hasMap(name)) {
                                ctx.getSource().sendFailure(
                                        Component.literal("§cMap '" + name + "' already exists.")
                                );
                            } else {
                                mm.addMap(name);
                                ctx.getSource().sendSuccess( () ->
                                        Component.literal("§aCreated map '" + name + "'."),
                                        false
                                );
                            }
                            return 1;
                        })
                )
        );


        dispatcher.register(literal("givePoints")
                .requires(src -> src.hasPermission(2))
                .then(argument("playerName", StringArgumentType.word())
                        .then(argument("points", IntegerArgumentType.integer())
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "playerName");
                                    int pts     = IntegerArgumentType.getInteger(ctx, "points");

                                    MinecraftServer server = ctx.getSource().getServer();
                                    PlayerList plist = server.getPlayerList();

                                    // find the target
                                    ServerPlayer target = plist.getPlayers().stream()
                                            .filter(p -> p.getGameProfile().getName().equalsIgnoreCase(name))
                                            .findFirst().orElse(null);

                                    if (target == null) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("§cNo online player named '" + name + "'")
                                        );
                                        return 0;
                                    }

                                    // update stats & save
                                    PlayerStatsManager mgr = PlayerStatsManager.get();
                                    mgr.updateStats(target.getUUID(), pts, 0);
                                    mgr.save();

                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("§aGave " + pts + " points to " + name),
                                            false
                                    );
                                    return 1;
                                })
                        )
                )
        );


        dispatcher.register(literal("giveXP")
                .requires(src -> src.hasPermission(2))
                .then(argument("playerName", StringArgumentType.word())
                        .then(argument("xp", IntegerArgumentType.integer())
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "playerName");
                                    int xp     = IntegerArgumentType.getInteger(ctx, "xp");

                                    MinecraftServer server = ctx.getSource().getServer();
                                    PlayerList plist = server.getPlayerList();

                                    // find the target
                                    ServerPlayer target = plist.getPlayers().stream()
                                            .filter(p -> p.getGameProfile().getName().equalsIgnoreCase(name))
                                            .findFirst().orElse(null);

                                    if (target == null) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("§cNo online player named '" + name + "'")
                                        );
                                        return 0;
                                    }

                                    // update stats & save
                                    PlayerStatsManager mgr = PlayerStatsManager.get();
                                    mgr.updateStats(target.getUUID(), 0, xp);
                                    mgr.save();

                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("§aGave " + xp + " points to " + name),
                                            false
                                    );
                                    return 1;
                                })
                        )
                )
        );

        dispatcher.register(literal("givePointsAndXP")
                .requires(src -> src.hasPermission(2))
                .then(argument("playerName", StringArgumentType.word())
                        .then(argument("points", IntegerArgumentType.integer())
                        .then(argument("xp", IntegerArgumentType.integer())
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "playerName");
                            int pts    = IntegerArgumentType.getInteger(ctx, "points");
                            int xp    = IntegerArgumentType.getInteger(ctx, "points");

                            MinecraftServer server = ctx.getSource().getServer();
                            PlayerList plist = server.getPlayerList();

                            // Manually find by username (case‐insensitive)
                            ServerPlayer target = plist.getPlayers().stream()
                                    .filter(p -> p.getGameProfile().getName().equalsIgnoreCase(name))
                                    .findFirst().orElse(null);

                            if (target == null) {
                                ctx.getSource().sendFailure(
                                        Component.literal("§cNo online player named '" + name + "'")
                                );
                                return 0;
                            }

                            // Update stats & save
                            var statsMgr = PlayerStatsManager.get();
                            statsMgr.updateStats(target.getUUID(), pts, xp);
                            statsMgr.save();

                            ctx.getSource().sendSuccess( () ->
                                    Component.literal(  "§aGave " + pts + " points and " + xp + " XP to" + name),
                                    false
                            );
                            return 1;
                        })
                )))
        );

        dispatcher.register(literal("deleteMap")
                .requires(src -> src.hasPermission(2))
                .then(argument("name", StringArgumentType.word())
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            MapManager mm = MapManager.get();
                            if (!mm.hasMap(name)) {
                                ctx.getSource().sendFailure(
                                        Component.literal("§cMap '" + name + " 'doesn't exist.")
                                );
                            } else {
                                mm.deleteMap(name);
                                ctx.getSource().sendSuccess( () ->
                                                Component.literal("§aDeleted map '" + name + "'."),
                                        false
                                );
                            }
                            return 1;
                        })
                )
        );

        dispatcher.register(literal("playableArea")
                .requires(src -> src.hasPermission(2))
                .then(argument("x1", IntegerArgumentType.integer())
                        .then(argument("y1", IntegerArgumentType.integer())
                                .then(argument("z1", IntegerArgumentType.integer())
                                        .then(argument("x2", IntegerArgumentType.integer())
                                                .then(argument("y2", IntegerArgumentType.integer())
                                                        .then(argument("z2", IntegerArgumentType.integer())
                                                                .then(argument("name", StringArgumentType.word())
                                                                        .executes(ctx -> {
                                                                            int x1 = IntegerArgumentType.getInteger(ctx, "x1");
                                                                            int y1 = IntegerArgumentType.getInteger(ctx, "y1");
                                                                            int z1 = IntegerArgumentType.getInteger(ctx, "z1");
                                                                            int x2 = IntegerArgumentType.getInteger(ctx, "x2");
                                                                            int y2 = IntegerArgumentType.getInteger(ctx, "y2");
                                                                            int z2 = IntegerArgumentType.getInteger(ctx, "z2");
                                                                            String name = StringArgumentType.getString(ctx, "name");

                                                                            MapManager mm = MapManager.get();
                                                                            if (!mm.hasMap(name)) {
                                                                                ctx.getSource().sendFailure(
                                                                                        Component.literal("§cNo map named '" + name + "' found.")
                                                                                );
                                                                            } else {
                                                                                mm.setPlayableArea(name,
                                                                                        new BlockPos(x1, y1, z1),
                                                                                        new BlockPos(x2, y2, z2)
                                                                                );
                                                                                ctx.getSource().sendSuccess( () ->
                                                                                        Component.literal("§aSet playable area for '" + name + "'."),
                                                                                        false
                                                                                );
                                                                            }
                                                                            return 1;
                                                                        })
                                                                ))))))));


        dispatcher.register(literal("setMapSpawn")
                .requires(src -> src.hasPermission(2))
                .then(argument("x", IntegerArgumentType.integer())
                        .then(argument("y", IntegerArgumentType.integer())
                                .then(argument("z", IntegerArgumentType.integer())
                                        .then(argument("name", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    int x = IntegerArgumentType.getInteger(ctx, "x");
                                                    int y = IntegerArgumentType.getInteger(ctx, "y");
                                                    int z = IntegerArgumentType.getInteger(ctx, "z");
                                                    String name = StringArgumentType.getString(ctx, "name");

                                                    MapManager mm = MapManager.get();
                                                    if (!mm.hasMap(name)) {
                                                        ctx.getSource().sendFailure(
                                                                Component.literal("§cNo map named '" + name + "' found.")
                                                        );
                                                    } else {
                                                        mm.setSpawnPoint(name, new BlockPos(x, y, z));
                                                        ctx.getSource().sendSuccess( () ->
                                                                Component.literal("§aSet spawn for '" + name + "'."),
                                                                false
                                                        );
                                                    }
                                                    return 1;
                                                })
                                        )))));
    }
}


