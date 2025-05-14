package com.infectedmod.logic.commands;


import com.infectedmod.InfectedMod;
import com.infectedmod.logic.*;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

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



        dispatcher.register(
                literal("endGame")
                        .requires(src -> src.hasPermission(4))
                        // ——— no-arg version: end *your* current session ———
                        .executes(ctx -> {
                            CommandSourceStack src = ctx.getSource();
                            ServerPlayer executor = src.getPlayerOrException();
                            SessionManager mgr = SessionManager.get();
                            Game game = mgr.getGameOfPlayer(executor.getUUID());
                            if (game == null) {
                                src.sendFailure(Component.literal("§cYou are not in a running game!"));
                                return 0;
                            }
                            // Broadcast & tear down
                            game.broadcastToSession("§cHost has ended this game. Use /joinGame to rejoin.");
                            // Remove all players from that session
                            GameSession session = mgr.getSessions().stream()
                                    .filter(s -> s.getSessionId() == game.getSessionId())
                                    .findFirst().orElse(null);
                            if (session != null) {
                                // copy to avoid concurrent-mod
                                List<UUID> players = new ArrayList<>(session.getPlayers());
                                for (UUID pid : players) {
                                    mgr.leaveSession(pid);
                                }
                                mgr.getSessions().remove(session);
                            }
                            game.stopGame(src.getServer());
                            src.sendSuccess(() -> Component.literal("§aYour game has been stopped"), true);
                            return 1;
                        })
                        // ——— with “id” argument: end session #id ———
                        .then(
                                argument("id", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            CommandSourceStack src = ctx.getSource();
                                            int id = IntegerArgumentType.getInteger(ctx, "id");
                                            SessionManager mgr = SessionManager.get();
                                            Game game = mgr.getGame(id);
                                            if (game == null) {
                                                src.sendFailure(Component.literal("§cNo running game with id " + id));
                                                return 0;
                                            }
                                            game.broadcastToSession("§cHost has ended game #" + id + ". Use /joinGame to rejoin.");
                                            // tear down that session
                                            GameSession session = mgr.getSessions().stream()
                                                    .filter(s -> s.getSessionId() == id)
                                                    .findFirst().orElse(null);
                                            if (session != null) {
                                                List<UUID> players = new ArrayList<>(session.getPlayers());
                                                for (UUID pid : players) {
                                                    mgr.leaveSession(pid);
                                                }
                                                mgr.getSessions().remove(session);
                                            }
                                            game.stopGame(src.getServer());
                                            src.sendSuccess(() -> Component.literal("§aGame #" + id + " has been stopped"), true);
                                            return 1;
                                        })
                        )
        );



        dispatcher.register(literal("setNextMap")
                .requires(src -> src.hasPermission(4))
                .then(argument("name", StringArgumentType.word())
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            MapManager mm = MapManager.get();
                            Game game = SessionManager.get().getGameOfPlayer(UUID.fromString(Objects.requireNonNull(ctx.getSource().getPlayer()).getUUID().toString()));
                            if (game != null && mm.hasMap(name) && (game.isRunning() || game.isIntermission())) {
                                game.setNextMap(mm.getMap(name));
                                ctx.getSource().sendSuccess( () ->
                                                Component.literal("§aNext map was set to '" + name + "'."),
                                        false
                                );
                            } else {
                                ctx.getSource().sendFailure(
                                        Component.literal("§cMap '" + name + "' doesn't exist.")
                                );

                            }
                            return 1;
                        })
                )
        );


        dispatcher.register(
                literal("joinGame")
                        .requires(src -> src.getEntity() instanceof ServerPlayer)

                        // 1) No-arg form
                        .executes(ctx -> {
                            try {
                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                UUID id = player.getUUID();
                                // join (or create) their session
                                Game game = SessionManager.get().joinSession(id);
                                ctx.getSource().sendSuccess(() ->
                                        Component.literal("§aJoined session #" + game.getSessionId()), false);
                                // ensure intermission (if nobody else)
                                if (!game.isIntermission() && !game.isRunning()) {
                                    game.startIntermission(ctx.getSource().getServer());
                                }
                                return game.getSessionId();
                            } catch (CommandSyntaxException e) {
                                ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
                                return 0;
                            } catch (Exception e) {
                                e.printStackTrace(); // so you see the NPE or whatever in console
                                ctx.getSource().sendFailure(Component.literal("§cAn unexpected error occurred"));
                                return 0;
                            }
                        })

                        // 2) “/joinGame <id>” form
                        .then(argument("id", IntegerArgumentType.integer(1, Integer.MAX_VALUE))
                                .executes(ctx -> {
                                    try {
                                        CommandSourceStack src = ctx.getSource();
                                        ServerPlayer player = src.getPlayerOrException();
                                        int sessionId = IntegerArgumentType.getInteger(ctx, "id");
                                        SessionManager mgr = SessionManager.get();
                                        Game game = mgr.getGame(sessionId);
                                        if (game == null) {
                                            src.sendFailure(Component.literal("§cNo session #" + sessionId));
                                            return 0;
                                        }
                                        mgr.joinSessionById(player.getUUID(), sessionId);
                                        src.sendSuccess(() ->
                                                Component.literal("§aJoined session #" + sessionId), false);
                                        if (!game.isIntermission() && !game.isRunning()) {
                                            game.startIntermission(src.getServer());
                                        }
                                        return sessionId;
                                    } catch (CommandSyntaxException e) {
                                        ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
                                        return 0;
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        ctx.getSource().sendFailure(Component.literal("§cAn unexpected error occurred"));
                                        return 0;
                                    }
                                })
                        )
        );



        dispatcher.register(literal("leaveGame")
                .requires(src -> src.getEntity() instanceof ServerPlayer)
                .executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    SessionManager.get().leaveSession(p.getUUID());
                    ctx.getSource().sendSuccess( () ->
                            Component.literal("You left your session."),
                            false
                    );
                    return 1;
                })
        );



        dispatcher.register(literal("nextMap")
                .requires(src -> src.hasPermission(1))
                        .executes(ctx -> {

                            MapManager mm = MapManager.get();
                            Game game = SessionManager.get().getGameOfPlayer(UUID.fromString(Objects.requireNonNull(ctx.getSource().getPlayer()).getUUID().toString()));
                            String name = game.getNextMap().name;
                            if (game != null && mm.hasMap(name) && (game.isRunning() || game.isIntermission())) {
                                ctx.getSource().sendSuccess( () ->
                                                Component.literal("§aNext map is '" + name + "'."),
                                        false
                                );
                            } else {
                                ctx.getSource().sendFailure(
                                        Component.literal("§cGame is not running '")
                                );

                            }
                            return 1;
                        })
        );


        dispatcher.register(literal("addMap")
                .requires(src -> src.hasPermission(4))
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
                .requires(src -> src.hasPermission(4))
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
                .requires(src -> src.hasPermission(4))
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


