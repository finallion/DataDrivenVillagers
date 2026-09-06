package com.lion.datadrivenvillagers.platform;

import com.lion.datadrivenvillagers.network.Payload;

import dev.architectury.injectables.annotations.ExpectPlatform;

import net.minecraft.server.network.ServerPlayerEntity;

/// Server side of the channels. 1.20.1 has no `CustomPayload` and no API both loaders share, so each
/// wires its own: Fabric API's play networking, Forge's `SimpleChannel`.
public class Network {

    /// Receivers for the two payloads a client sends. Called from the loader's initializer.
    @ExpectPlatform
    public static void register() {
        throw new AssertionError();
    }

    /// Does nothing for a client that cannot receive the payload.
    @ExpectPlatform
    public static void send(ServerPlayerEntity player, Payload payload) {
        throw new AssertionError();
    }
}
