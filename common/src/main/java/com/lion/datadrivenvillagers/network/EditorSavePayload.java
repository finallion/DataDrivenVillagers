package com.lion.datadrivenvillagers.network;

import com.lion.datadrivenvillagers.DataDrivenVillagers;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/// Client to server: write a profession file. Untrusted: the server checks the name against a pattern
/// and runs the json through the same parser as a file from disk.
///
/// @param fileName file name without `.json`
/// @param json     the file to write, as the editor assembled it
public record EditorSavePayload(String fileName, String json) implements CustomPayload {

    public static final Id<EditorSavePayload> ID = new Id<>(DataDrivenVillagers.id("editor_save"));

    public static final PacketCodec<RegistryByteBuf, EditorSavePayload> CODEC =
            PacketCodec.of(EditorSavePayload::write, EditorSavePayload::read);

    private static void write(EditorSavePayload payload, RegistryByteBuf buf) {
        buf.writeString(payload.fileName());
        buf.writeString(payload.json(), EditorOpenPayload.MAX_JSON);
    }

    private static EditorSavePayload read(RegistryByteBuf buf) {
        return new EditorSavePayload(buf.readString(), buf.readString(EditorOpenPayload.MAX_JSON));
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
