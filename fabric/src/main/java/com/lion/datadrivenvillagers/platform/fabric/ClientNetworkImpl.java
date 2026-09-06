package com.lion.datadrivenvillagers.platform.fabric;

import com.lion.datadrivenvillagers.network.EditorBridge;
import com.lion.datadrivenvillagers.network.EditorOpenPayload;
import com.lion.datadrivenvillagers.network.EditorResultPayload;
import com.lion.datadrivenvillagers.network.LookPayload;
import com.lion.datadrivenvillagers.network.LooksBeginPayload;
import com.lion.datadrivenvillagers.network.Payload;
import com.lion.datadrivenvillagers.network.SyncedLooks;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;

public class ClientNetworkImpl {

    /// Read on the netty thread, before the buffer is released. The work is queued onto the client
    /// thread, which is the render thread, so {@link SyncedLooks} needs no lock.
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(LooksBeginPayload.ID, (client, handler, buf, sender) -> {
            LooksBeginPayload payload = LooksBeginPayload.read(buf);
            client.execute(() -> SyncedLooks.begin(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(LookPayload.ID, (client, handler, buf, sender) -> {
            LookPayload payload = LookPayload.read(buf);
            client.execute(() -> SyncedLooks.accept(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(EditorOpenPayload.ID, (client, handler, buf, sender) -> {
            EditorOpenPayload payload = EditorOpenPayload.read(buf);
            client.execute(() -> EditorBridge.open(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(EditorResultPayload.ID, (client, handler, buf, sender) -> {
            EditorResultPayload payload = EditorResultPayload.read(buf);
            client.execute(() -> EditorBridge.result(payload));
        });
    }

    /// No canSend check: the editor screen only opens because the server sent it.
    public static void send(Payload payload) {
        PacketByteBuf buf = PacketByteBufs.create();
        payload.write(buf);
        ClientPlayNetworking.send(payload.id(), buf);
    }
}
