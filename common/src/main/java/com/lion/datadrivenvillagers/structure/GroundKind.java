package com.lion.datadrivenvillagers.structure;

import com.lion.datadrivenvillagers.DefinitionParseException;

import java.util.Locale;

/// Mirrors `StructurePool.Projection` so the parser stays free of Minecraft classes, like
/// {@link com.lion.datadrivenvillagers.profession.HatKind}. `rigid` is what vanilla houses use,
/// `terrain` what vanilla paths use.
public enum GroundKind {
    RIGID,
    TERRAIN;

    public static GroundKind parse(String raw) {
        for (GroundKind kind : values()) {
            if (kind.name().toLowerCase(Locale.ROOT).equals(raw.toLowerCase(Locale.ROOT))) {
                return kind;
            }
        }
        throw new DefinitionParseException("unknown ground \"" + raw + "\", expected rigid or terrain");
    }
}
