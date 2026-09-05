package com.lion.datadrivenvillagers.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/// A custom packet: channel id and writer on the record, the reader as a static method beside it.
public interface Payload {

    Identifier id();

    void write(PacketByteBuf buf);
}
