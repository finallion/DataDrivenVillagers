package com.lion.datadrivenvillagers.network;

import com.lion.datadrivenvillagers.DataDrivenVillagers;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/// Server to client, before the first {@link LookPayload}: a server with this mod is now the authority
/// on looks. Sent alone when the server has no definitions, so the client stops using its own folder
/// even then.
///
/// @param count how many looks follow, for the log only
public record LooksBeginPayload(int count) implements Payload {

    public static final Identifier ID = DataDrivenVillagers.id("looks_begin");

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeVarInt(count);
    }

    public static LooksBeginPayload read(PacketByteBuf buf) {
        return new LooksBeginPayload(buf.readVarInt());
    }
}
