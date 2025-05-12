package com.infectedmod.logic.commands;


import com.infectedmod.InfectedMod;
import com.infectedmod.logic.*;
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

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

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



        dispatcher.register(literal("endGame")
                .requires(source -> source.hasPermission(4))
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    MinecraftServer server = src.getServer();
                    SessionManager sessionManager = SessionManager.get();
                    UUID playerUUID = Objects.requireNonNull(src.getPlayer()).getUUID();
                    Game game = sessionManager.getGameOfPlayer(playerUUID);
                    if(game == null) {ctx.getSource().sendFailure(
                            Component.literal("§cGame is not running!.")
                    );}
                    else{
                        for(UUID playerId: sessionManager.getSessions().get(game.getSessionId()).getPlayers()){
                            game.broadcastToSession("§cHost has ended this game. Use /joinGame to join another game.");
                            sessionManager.leaveSession(playerId);
                            sessionManager.getSessions().remove(game.getSessionId());
                        }
                        game.stopGame(server);
                        src.sendSuccess(() -> Component.literal("The Game has been stopped"), true);
                    }
                    return 1;
                })

                .then(argument("id",    IntegerArgumentType.integer(0)))
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    MinecraftServer server = src.getServer();
                    int id = IntegerArgumentType.getInteger(ctx, "id");
                    SessionManager sessionManager = SessionManager.get();
                    Game game = sessionManager.getGame(id);
                    if(game == null) {ctx.getSource().sendFailure(
                            Component.literal("§cGame is not running!.")
                    );}
                    for(UUID playerId: sessionManager.getSessions().get(game.getSessionId()).getPlayers()){
                        game.broadcastToSession("§cHost has ended this game. Use /joinGame to join another game.");
                        sessionManager.leaveSession(playerId);
                        sessionManager.getSessions().remove(game.getSessionId());
                    }
                    game.stopGame(server);
                    src.sendSuccess(() -> Component.literal("The Game has been stopped"), true);
                    return 2;
                })
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


        dispatcher.register(literal("joinGame")
                .requires(src -> src.getEntity() instanceof ServerPlayer)
                .executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    Game game = SessionManager.get().joinSession(p.getUUID());
                    ctx.getSource().sendSuccess( () ->
                            Component.literal("Joined session #" + game.getSessionId()),
                            false
                    );
                    return game.getSessionId();
                })
                .then(argument("id",    IntegerArgumentType.integer(0)))
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    MinecraftServer server = src.getServer();
                    int id = IntegerArgumentType.getInteger(ctx, "id");
                    SessionManager sessionManager = SessionManager.get();
                    Game game = sessionManager.getGame(id);
                    sessionManager.joinSessionById(p.getUUID(), id);
                    ctx.getSource().sendSuccess( () ->
                                    Component.literal("Joined session #" + game.getSessionId()),
                            false
                    );
                    return 2;
                })
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


