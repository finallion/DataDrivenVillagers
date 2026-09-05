package com.lion.datadrivenvillagers.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;

import net.minecraft.network.packet.CustomPayload;

/// Client to server. Separate from {@link Network} so the dedicated server never loads a client-only
/// call.
public class ClientNetwork {

    @ExpectPlatform
    public static void send(CustomPayload payload) {
        throw new AssertionError();
    }
}
