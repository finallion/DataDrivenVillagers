package com.lion.datadrivenvillagers.network;

import com.lion.datadrivenvillagers.DataDrivenVillagers;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/// Client to server: write the VillagerTradingPlus starting file for one profession into the world's
/// datapacks. Only the name travels; the content is built on the server as `/ddv scaffold` builds it.
///
/// @param fileName file name without `.json`
public record EditorTradesPayload(String fileName) implements CustomPayload {

    public static final Id<EditorTradesPayload> ID = new Id<>(DataDrivenVillagers.id("editor_trades"));

    public static final PacketCodec<RegistryByteBuf, EditorTradesPayload> CODEC =
            PacketCodec.of(EditorTradesPayload::write, EditorTradesPayload::read);

    private static void write(EditorTradesPayload payload, RegistryByteBuf buf) {
        buf.writeString(payload.fileName());
    }

    private static EditorTradesPayload read(RegistryByteBuf buf) {
        return new EditorTradesPayload(buf.readString());
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
