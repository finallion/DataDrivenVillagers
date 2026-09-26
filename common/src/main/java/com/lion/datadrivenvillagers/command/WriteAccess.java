package com.lion.datadrivenvillagers.command;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/// Level 3, and on an integrated server the host only. Gates every subcommand and payload that writes
/// to disk. A non-player source, console or rcon, passes once the level does.
final class WriteAccess {

    private static final int LEVEL = 3;

    private WriteAccess() {
    }

    static boolean allowed(ServerCommandSource source) {
        if (!source.hasPermissionLevel(LEVEL)) {
            return false;
        }
        ServerPlayerEntity player = source.getPlayer();
        return player == null || hostOrDedicated(source.getServer(), player);
    }

    static boolean allowed(ServerPlayerEntity player) {
        return player.hasPermissionLevel(LEVEL) && hostOrDedicated(player.getServer(), player);
    }

    private static boolean hostOrDedicated(MinecraftServer server, ServerPlayerEntity player) {
        return server.isDedicated() || server.isHost(player.getGameProfile());
    }
}
