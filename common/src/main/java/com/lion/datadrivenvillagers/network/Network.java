package com.lion.datadrivenvillagers.network;

import com.lion.datadrivenvillagers.command.EditCommand;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;

/// Server side of the channels, through Architectury on both loaders. The two payloads registered
/// here are the only ones a client sends, and their contents are untrusted.
public final class Network {

    private Network() {
    }

    /// Reads on the network thread, before the buffer is released; the work itself is queued.
    public static void register() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, EditorSavePayload.ID, (buf, context) -> {
            EditorSavePayload payload = EditorSavePayload.read(buf);
            context.queue(() -> EditCommand.save((ServerPlayerEntity) context.getPlayer(), payload));
        });
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, EditorTradesPayload.ID, (buf, context) -> {
            EditorTradesPayload payload = EditorTradesPayload.read(buf);
            context.queue(() -> EditCommand.trades((ServerPlayerEntity) context.getPlayer(), payload));
        });
    }

    /// Does nothing for a client that cannot receive the payload.
    public static void send(ServerPlayerEntity player, Payload payload) {
        if (!NetworkManager.canPlayerReceive(player, payload.id())) {
            return;
        }
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        payload.write(buf);
        NetworkManager.sendToPlayer(player, payload.id(), buf);
    }
}
