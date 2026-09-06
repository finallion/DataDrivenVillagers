package com.lion.datadrivenvillagers.platform.fabric;

import com.lion.datadrivenvillagers.command.EditCommand;
import com.lion.datadrivenvillagers.network.EditorSavePayload;
import com.lion.datadrivenvillagers.network.EditorTradesPayload;
import com.lion.datadrivenvillagers.network.Payload;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;

public class NetworkImpl {

    /// Fabric calls the handler on the netty thread: the payload is read there, before the buffer is
    /// released, and only the work is queued onto the server thread. Both payloads are untrusted.
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(EditorSavePayload.ID, (server, player, handler, buf, sender) -> {
            EditorSavePayload payload = EditorSavePayload.read(buf);
            server.execute(() -> EditCommand.save(player, payload));
        });
        ServerPlayNetworking.registerGlobalReceiver(EditorTradesPayload.ID, (server, player, handler, buf, sender) -> {
            EditorTradesPayload payload = EditorTradesPayload.read(buf);
            server.execute(() -> EditCommand.trades(player, payload));
        });
    }

    /// Fabric lets a client without this mod join; a packet on a channel it never registered drops
    /// the connection.
    public static void send(ServerPlayerEntity player, Payload payload) {
        if (!ServerPlayNetworking.canSend(player, payload.id())) {
            return;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        payload.write(buf);
        ServerPlayNetworking.send(player, payload.id(), buf);
    }
}
