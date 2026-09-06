package com.lion.datadrivenvillagers.platform.forge;

import com.lion.datadrivenvillagers.network.Payload;

public class ClientNetworkImpl {

    /// Nothing left to do: the six packets need the same indices on both sides, so
    /// {@link NetworkImpl#register()} builds the channel for the client as well.
    public static void register() {
    }

    public static void send(Payload payload) {
        Channel.INSTANCE.sendToServer(payload);
    }
}
