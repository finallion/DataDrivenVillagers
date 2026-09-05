package com.lion.datadrivenvillagers.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;

import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

/// Server to one player. Payload types and receivers are registered in each loader's initializer.
public class Network {

    /// Does nothing for a client that cannot receive the payload (on Fabric: a client without this mod).
    @ExpectPlatform
    public static void send(ServerPlayerEntity player, CustomPayload payload) {
        throw new AssertionError();
    }
}
