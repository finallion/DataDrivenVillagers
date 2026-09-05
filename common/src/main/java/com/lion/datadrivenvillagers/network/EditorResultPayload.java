package com.lion.datadrivenvillagers.network;

import com.lion.datadrivenvillagers.DataDrivenVillagers;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.List;

/// Server to client: the outcome of a save or trades request, one level per line.
///
/// @param ok    whether the request succeeded; false means the file on disk is untouched
/// @param notes phrased for a pack author, the reason first
public record EditorResultPayload(boolean ok, List<Note> notes) implements CustomPayload {

    /// `WARN`: the save worked but the profession is not in the game yet (restart needed).
    public enum Level {
        OK, WARN, BAD
    }

    public record Note(Level level, String text) {
    }

    public static final Id<EditorResultPayload> ID = new Id<>(DataDrivenVillagers.id("editor_result"));

    public static final PacketCodec<RegistryByteBuf, EditorResultPayload> CODEC =
            PacketCodec.of(EditorResultPayload::write, EditorResultPayload::read);

    public static Note ok(String text) {
        return new Note(Level.OK, text);
    }

    public static Note warn(String text) {
        return new Note(Level.WARN, text);
    }

    public static Note bad(String text) {
        return new Note(Level.BAD, text);
    }

    private static void write(EditorResultPayload payload, RegistryByteBuf buf) {
        buf.writeBoolean(payload.ok());
        buf.writeCollection(payload.notes(), (target, note) -> {
            target.writeEnumConstant(note.level());
            target.writeString(note.text());
        });
    }

    private static EditorResultPayload read(RegistryByteBuf buf) {
        return new EditorResultPayload(buf.readBoolean(),
                buf.readList(source -> new Note(source.readEnumConstant(Level.class), source.readString())));
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
