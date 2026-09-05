package com.lion.datadrivenvillagers.network;

import com.lion.datadrivenvillagers.DataDrivenVillagers;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.List;

/// Server to client: the outcome of a save or trades request, one level per line.
///
/// @param ok    whether the request succeeded; false means the file on disk is untouched
/// @param notes phrased for a pack author, the reason first
public record EditorResultPayload(boolean ok, List<Note> notes) implements Payload {

    /// `WARN`: the save worked but the profession is not in the game yet (restart needed).
    public enum Level {
        OK, WARN, BAD
    }

    public record Note(Level level, String text) {
    }

    public static final Identifier ID = DataDrivenVillagers.id("editor_result");

    public static Note ok(String text) {
        return new Note(Level.OK, text);
    }

    public static Note warn(String text) {
        return new Note(Level.WARN, text);
    }

    public static Note bad(String text) {
        return new Note(Level.BAD, text);
    }

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeBoolean(ok);
        buf.writeCollection(notes, (target, note) -> {
            target.writeEnumConstant(note.level());
            target.writeString(note.text());
        });
    }

    public static EditorResultPayload read(PacketByteBuf buf) {
        return new EditorResultPayload(buf.readBoolean(),
                buf.readList(source -> new Note(source.readEnumConstant(Level.class), source.readString())));
    }
}
