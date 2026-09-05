package com.lion.datadrivenvillagers.platform.neoforge;

import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public class NetworkImpl {

    /// No canSend check: neoforge.mods.toml requires the mod on both sides.
    public static void send(ServerPlayerEntity player, CustomPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
