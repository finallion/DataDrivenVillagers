package com.lion.datadrivenvillagers.network;

import com.lion.datadrivenvillagers.DataDrivenVillagers;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/// Client to server: write a profession file. Untrusted: the server checks the name against a pattern
/// and runs the json through the same parser as a file from disk.
///
/// @param fileName file name without `.json`
/// @param json     the file to write, as the editor assembled it
public record EditorSavePayload(String fileName, String json) implements Payload {

    public static final Identifier ID = DataDrivenVillagers.id("editor_save");

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeString(fileName);
        buf.writeString(json, EditorOpenPayload.MAX_JSON);
    }

    public static EditorSavePayload read(PacketByteBuf buf) {
        return new EditorSavePayload(buf.readString(), buf.readString(EditorOpenPayload.MAX_JSON));
    }
}
