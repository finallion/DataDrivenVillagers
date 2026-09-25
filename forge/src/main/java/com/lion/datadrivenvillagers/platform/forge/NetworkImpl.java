package com.lion.datadrivenvillagers.platform.forge;

import com.lion.datadrivenvillagers.network.Payload;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraftforge.network.PacketDistributor;

public class NetworkImpl {

    public static void register() {
        Channel.register();
    }

    /// No check for the receiving side: Forge refuses a client that does not carry this mod to connect.
    public static void send(ServerPlayerEntity player, Payload payload) {
        Channel.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }
}
