package com.lion.datadrivenvillagers.type;

import com.google.gson.JsonObject;
import com.lion.datadrivenvillagers.DataDrivenVillagers;
import com.lion.datadrivenvillagers.DefinitionParseException;
import com.lion.datadrivenvillagers.JsonFields;

import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/// Turns one json file into a {@link TypeDefinition}. No registry access, so it is unit testable.
public final class TypeParser {

    private static final String TAG_PREFIX = "#";

    private TypeParser() {
    }

    /// @param fileName file name without extension, becomes the registry path
    public static TypeDefinition parse(String fileName, JsonObject root) {
        String path = JsonFields.idPathFromFileName(fileName);
        JsonFields.TextureSource texture = JsonFields.texture(root, "texture");

        List<Identifier> biomes = new ArrayList<>();
        List<Identifier> biomeTags = new ArrayList<>();
        for (String raw : JsonFields.strings(root, "biomes")) {
            // # marks a tag, as in datapacks.
            if (raw.startsWith(TAG_PREFIX)) {
                biomeTags.add(JsonFields.identifier(raw.substring(TAG_PREFIX.length())));
            } else {
                biomes.add(JsonFields.identifier(raw));
            }
        }

        if (biomes.isEmpty() && biomeTags.isEmpty()) {
            throw new DefinitionParseException(
                    "\"biomes\" must name at least one biome or #tag, a type nothing maps to is never used");
        }

        return new TypeDefinition(
                DataDrivenVillagers.id(path),
                texture.identifier(),
                texture.file(),
                List.copyOf(biomes),
                List.copyOf(biomeTags));
    }
}
