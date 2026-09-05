package com.lion.datadrivenvillagers.type;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lion.datadrivenvillagers.DefinitionParseException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Covers the villager type format. Same split as the profession parser: nothing here touches a
/// registry, so every rule a pack author can trip over is checked without booting the game.
class TypeParserTest {

    private static JsonObject json(String raw) {
        return JsonParser.parseString(raw).getAsJsonObject();
    }

    private static TypeDefinition parse(String name, String raw) {
        return TypeParser.parse(name, json(raw));
    }

    @Test
    void readsAFullDefinition() {
        TypeDefinition definition = parse("reef", """
                {
                  "texture": "reef.png",
                  "biomes": ["minecraft:warm_ocean", "minecraft:lukewarm_ocean"]
                }
                """);

        assertEquals("datadrivenvillagers:reef", definition.id().toString());
        assertEquals("reef.png", definition.textureFile().orElseThrow());
        assertEquals(2, definition.biomes().size());
        assertTrue(definition.biomeTags().isEmpty());
    }

    /// The # prefix is the only thing that separates the two, and getting it wrong would either map
    /// nothing at all or map a tag id as if it were a biome.
    @Test
    void separatesBiomeTagsFromBiomeIds() {
        TypeDefinition definition = parse("cold", """
                { "biomes": ["minecraft:snowy_plains", "#minecraft:is_taiga"] }
                """);

        assertEquals(1, definition.biomes().size());
        assertEquals("minecraft:snowy_plains", definition.biomes().get(0).toString());
        assertEquals(1, definition.biomeTags().size());
        assertEquals("minecraft:is_taiga", definition.biomeTags().get(0).toString());
    }

    @Test
    void acceptsASingleBiomeWithoutAList() {
        assertEquals(1, parse("one", """
                { "biomes": "minecraft:desert" }
                """).biomes().size());
    }

    /// Same rule as a profession texture, and it lives in one place for exactly that reason.
    @Test
    void separatesTextureFileFromTextureIdentifier() {
        assertEquals("reef.png", parse("a", """
                { "biomes": "minecraft:desert", "texture": "reef.png" }
                """).textureFile().orElseThrow());

        assertEquals("mypack:textures/entity/villager/type/reef.png", parse("b", """
                { "biomes": "minecraft:desert", "texture": "mypack:textures/entity/villager/type/reef.png" }
                """).texture().orElseThrow().toString());
    }

    /// A type nothing maps to can never be assigned, so it is a broken file rather than an empty one.
    @Test
    void rejectsATypeThatClaimsNoBiome() {
        DefinitionParseException e = assertThrows(DefinitionParseException.class,
                () -> parse("nowhere", "{}"));
        assertTrue(e.getMessage().contains("biomes"));
    }

    @Test
    void rejectsAMalformedBiomeId() {
        assertThrows(DefinitionParseException.class, () -> parse("x", """
                { "biomes": "Not An Id" }
                """));
    }

    @Test
    void rejectsAFileNameThatCannotBeAnId() {
        assertThrows(DefinitionParseException.class, () -> parse("Bad Name", """
                { "biomes": "minecraft:desert" }
                """));
    }
}
