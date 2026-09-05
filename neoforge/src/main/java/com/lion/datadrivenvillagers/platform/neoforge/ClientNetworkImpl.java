package com.lion.datadrivenvillagers.platform.neoforge;

import net.minecraft.network.packet.CustomPayload;
import net.neoforged.neoforge.network.PacketDistributor;

public class ClientNetworkImpl {

    public static void send(CustomPayload payload) {
        PacketDistributor.sendToServer(payload);
    }
}
