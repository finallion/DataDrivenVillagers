package com.lion.datadrivenvillagers.network;

import com.lion.datadrivenvillagers.DataDrivenVillagers;
import com.lion.datadrivenvillagers.network.EditorResultPayload.Level;
import com.lion.datadrivenvillagers.network.EditorResultPayload.Note;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/// Server to client: open the editor on a file. The raw file text travels, not a parsed definition,
/// so `_comment` and unknown fields survive a save.
///
/// @param fileName file name without `.json`, empty for a new file
/// @param json     the file as on disk, or a template when it does not exist yet
/// @param state    what the running game holds for this file, already phrased
public record EditorOpenPayload(String fileName, String json, Note state) implements Payload {

    static final int MAX_JSON = 262144;

    public static final Identifier ID = DataDrivenVillagers.id("editor_open");

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeString(fileName);
        buf.writeString(json, MAX_JSON);
        buf.writeEnumConstant(state.level());
        buf.writeString(state.text());
    }

    public static EditorOpenPayload read(PacketByteBuf buf) {
        return new EditorOpenPayload(buf.readString(), buf.readString(MAX_JSON),
                new Note(buf.readEnumConstant(Level.class), buf.readString()));
    }
}
