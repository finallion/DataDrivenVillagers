package com.lion.datadrivenvillagers.network;

import com.lion.datadrivenvillagers.DataDrivenVillagers;
import com.lion.datadrivenvillagers.network.EditorResultPayload.Level;
import com.lion.datadrivenvillagers.network.EditorResultPayload.Note;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/// Server to client: open the editor on a file. The raw file text travels, not a parsed definition,
/// so `_comment` and unknown fields survive a save.
///
/// @param fileName file name without `.json`, empty for a new file
/// @param json     the file as on disk, or a template when it does not exist yet
/// @param state    what the running game holds for this file, already phrased
public record EditorOpenPayload(String fileName, String json, Note state) implements CustomPayload {

    static final int MAX_JSON = 262144;

    public static final Id<EditorOpenPayload> ID = new Id<>(DataDrivenVillagers.id("editor_open"));

    public static final PacketCodec<RegistryByteBuf, EditorOpenPayload> CODEC =
            PacketCodec.of(EditorOpenPayload::write, EditorOpenPayload::read);

    private static void write(EditorOpenPayload payload, RegistryByteBuf buf) {
        buf.writeString(payload.fileName());
        buf.writeString(payload.json(), MAX_JSON);
        buf.writeEnumConstant(payload.state().level());
        buf.writeString(payload.state().text());
    }

    private static EditorOpenPayload read(RegistryByteBuf buf) {
        return new EditorOpenPayload(buf.readString(), buf.readString(MAX_JSON),
                new Note(buf.readEnumConstant(Level.class), buf.readString()));
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
