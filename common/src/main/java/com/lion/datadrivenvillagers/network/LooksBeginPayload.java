package com.lion.datadrivenvillagers.network;

import com.lion.datadrivenvillagers.DataDrivenVillagers;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/// Server to client, before the first {@link LookPayload}: a server with this mod is now the authority
/// on looks. Sent alone when the server has no definitions, so the client stops using its own folder
/// even then.
///
/// @param count how many looks follow, for the log only
public record LooksBeginPayload(int count) implements CustomPayload {

    public static final Id<LooksBeginPayload> ID = new Id<>(DataDrivenVillagers.id("looks_begin"));

    public static final PacketCodec<RegistryByteBuf, LooksBeginPayload> CODEC = PacketCodec.of(
            (payload, buf) -> buf.writeVarInt(payload.count()),
            buf -> new LooksBeginPayload(buf.readVarInt()));

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
