package com.lion.datadrivenvillagers.command;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/// A `/ddv why` answer under construction: steps, notes under them, a verdict, extras after it.
/// Lines are collected because the verdict is only known after every step; `/ddv doctor` takes the
/// same lines as plain text.
final class Report {

    private final List<Text> lines = new ArrayList<>();
    private String firstBreak;

    void header(Identifier id, String kind) {
        header(id.toString(), kind);
    }

    void header(String title, String kind) {
        lines.add(Text.literal(title).formatted(Formatting.GOLD)
                .append(Text.literal("  " + kind).formatted(Formatting.GRAY)));
    }

    void ok(String title, String detail) {
        step(Formatting.GREEN, "[ok] ", title, detail);
    }

    void broken(String title, String detail, String fix) {
        step(Formatting.RED, "[no] ", title, detail);
        lines.add(Text.literal("     -> " + fix).formatted(Formatting.YELLOW));
        if (firstBreak == null) {
            firstBreak = title;
        }
    }

    /// Yellow step; does not count as broken.
    void warn(String title, String detail) {
        step(Formatting.YELLOW, "[??] ", title, detail);
    }

    /// A step that could not be judged; printed so it does not read as passed.
    void skipped(String title, String detail) {
        step(Formatting.DARK_GRAY, "[--] ", title, detail.isEmpty() ? "not checked" : detail);
    }

    void notes(List<Note> notes) {
        notes(notes, Formatting.GRAY);
    }

    /// @param good colour for good notes; grey for plain listings, green when the note is the answer
    void notes(List<Note> notes, Formatting good) {
        for (Note note : notes) {
            lines.add(Text.literal("       " + note.text())
                    .formatted(note.good() ? good : Formatting.RED));
        }
    }

    /// Followed by a blank line to separate it from the extras.
    void verdict(boolean good, String text) {
        lines.add(Text.literal(text).formatted(good ? Formatting.GREEN : Formatting.RED));
        lines.add(Text.empty());
    }

    void extra(String label, Text text) {
        lines.add(Text.literal(label + "  ").formatted(Formatting.DARK_AQUA).append(text));
    }

    boolean isBroken() {
        return firstBreak != null;
    }

    String firstBreak() {
        return firstBreak;
    }

    int send(ServerCommandSource source) {
        for (Text line : lines) {
            source.sendFeedback(() -> line, false);
        }
        return isBroken() ? 0 : 1;
    }

    /// The same lines without colour; the `[ok]`/`[no]` marks survive.
    List<String> plain() {
        List<String> out = new ArrayList<>();
        for (Text line : lines) {
            out.add(line.getString());
        }
        return out;
    }

    private void step(Formatting colour, String mark, String title, String detail) {
        MutableText line = Text.literal(mark).formatted(colour)
                .append(Text.literal(title).formatted(Formatting.WHITE));
        if (!detail.isEmpty()) {
            line.append(Text.literal("  " + detail).formatted(Formatting.GRAY));
        }
        lines.add(line);
    }
}
