package com.lion.datadrivenvillagers.platform.fabric;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.packet.CustomPayload;

public class ClientNetworkImpl {

    /// No canSend check: the editor screen only opens because the server sent it.
    public static void send(CustomPayload payload) {
        ClientPlayNetworking.send(payload);
    }
}
