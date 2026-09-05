package com.lion.datadrivenvillagers.platform.neoforge;

import net.minecraft.network.packet.CustomPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class ClientNetworkImpl {

    /// `ClientPacketDistributor` is client-only; `PacketDistributor` cannot send to the server.
    public static void send(CustomPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }
}
