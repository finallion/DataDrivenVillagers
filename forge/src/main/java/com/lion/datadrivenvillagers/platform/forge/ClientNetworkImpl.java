package com.lion.datadrivenvillagers.platform.forge;

import com.lion.datadrivenvillagers.network.Payload;

public class ClientNetworkImpl {

    /// The six packets share indices, so {@link NetworkImpl#register()} already builds the channel for both sides.
    public static void register() {
    }

    public static void send(Payload payload) {
        Channel.INSTANCE.sendToServer(payload);
    }
}
