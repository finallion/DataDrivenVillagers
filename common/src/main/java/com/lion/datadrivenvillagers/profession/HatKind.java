package com.lion.datadrivenvillagers.profession;

import com.lion.datadrivenvillagers.DefinitionParseException;

import java.util.Locale;

/// Mirrors `VillagerResourceMetadata.HatType`, which is client only; definitions are parsed on the
/// server too.
public enum HatKind {
    NONE,
    PARTIAL,
    FULL;

    public static HatKind parse(String raw) {
        for (HatKind kind : values()) {
            if (kind.name().toLowerCase(Locale.ROOT).equals(raw.toLowerCase(Locale.ROOT))) {
                return kind;
            }
        }
        throw new DefinitionParseException("unknown hat \"" + raw + "\", expected none, partial or full");
    }
}
