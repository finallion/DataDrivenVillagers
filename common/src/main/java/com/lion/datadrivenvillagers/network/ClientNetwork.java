package com.lion.datadrivenvillagers.network;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;

/// Client side of the channels. Separate from {@link Network} so the dedicated server never loads a
/// client-only call.
public final class ClientNetwork {

    private ClientNetwork() {
    }

    /// Reads on the network thread, before the buffer is released. The work is queued onto the client
    /// thread, which is the render thread, so {@link SyncedLooks} needs no lock.
    public static void register() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, LooksBeginPayload.ID, (buf, context) -> {
            LooksBeginPayload payload = LooksBeginPayload.read(buf);
            context.queue(() -> SyncedLooks.begin(payload));
        });
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, LookPayload.ID, (buf, context) -> {
            LookPayload payload = LookPayload.read(buf);
            context.queue(() -> SyncedLooks.accept(payload));
        });
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, EditorOpenPayload.ID, (buf, context) -> {
            EditorOpenPayload payload = EditorOpenPayload.read(buf);
            context.queue(() -> EditorBridge.open(payload));
        });
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, EditorResultPayload.ID, (buf, context) -> {
            EditorResultPayload payload = EditorResultPayload.read(buf);
            context.queue(() -> EditorBridge.result(payload));
        });
    }

    public static void send(Payload payload) {
        if (!NetworkManager.canServerReceive(payload.id())) {
            return;
        }
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        payload.write(buf);
        NetworkManager.sendToServer(payload.id(), buf);
    }
}
