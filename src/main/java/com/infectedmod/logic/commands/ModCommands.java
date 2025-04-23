package com.infectedmod.logic.commands;


import com.infectedmod.logic.Game;
import com.infectedmod.logic.MapManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class ModCommands {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // /startRound
        dispatcher.register(Commands.literal("startRound")
                .requires(source -> source.hasPermission(4))
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    MinecraftServer server = src.getServer();
                    Game.get().startIntermission(server);
                    src.sendSuccess(() -> Component.literal("Intermission started."), true);
                    return 1;
                })
        );

        // /addMap <name>
        dispatcher.register(Commands.literal("addMap")
                .requires(source -> source.hasPermission(4))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            MapManager.get().addMap(name);
                            ctx.getSource().sendSuccess(() -> Component.literal("Map '" + name + "' added."), true);
                            return 1;
                        })
                )
        );

        // /playableArea <pos1> <pos2> <name>
        dispatcher.register(Commands.literal("playableArea")
                .requires(source -> source.hasPermission(4))
                .then(Commands.argument("pos1", BlockPosArgument.blockPos())
                        .then(Commands.argument("pos2", BlockPosArgument.blockPos())
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> {
                                            BlockPos p1 = BlockPosArgument.getLoadedBlockPos(ctx, "pos1");
                                            BlockPos p2 = BlockPosArgument.getLoadedBlockPos(ctx, "pos2");
                                            String name = StringArgumentType.getString(ctx, "name");
                                            MapManager.get().setPlayableArea(name, p1, p2);
                                            ctx.getSource().sendSuccess(() -> Component.literal("Playable area set for '" + name + "'."), true);
                                            return 1;
                                        })
                                )
                        )
                )
        );

        // /setMapSpawn <pos> <name>
        dispatcher.register(Commands.literal("setMapSpawn")
                .requires(source -> source.hasPermission(4))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                    BlockPos spawn = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
                                    String name = StringArgumentType.getString(ctx, "name");
                                    MapManager.get().setSpawnPoint(name, spawn);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Spawn point set for '" + name + "'."), true);
                                    return 1;
                                })
                        )
                )
        );
    }
}


