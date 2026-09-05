package com.lion.datadrivenvillagers.network;

import com.lion.datadrivenvillagers.DataDrivenVillagers;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/// Client to server: write the VillagerTradingPlus starting file for one profession into the world's
/// datapacks. Only the name travels; the content is built on the server as `/ddv scaffold` builds it.
///
/// @param fileName file name without `.json`
public record EditorTradesPayload(String fileName) implements Payload {

    public static final Identifier ID = DataDrivenVillagers.id("editor_trades");

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeString(fileName);
    }

    public static EditorTradesPayload read(PacketByteBuf buf) {
        return new EditorTradesPayload(buf.readString());
    }
}
