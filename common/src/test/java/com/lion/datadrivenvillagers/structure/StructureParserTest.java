package com.lion.datadrivenvillagers.structure;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lion.datadrivenvillagers.DefinitionParseException;
import net.minecraft.util.Identifier;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Covers the structure format, and above all the shorthand: a file with two fields has to end up
/// naming five pools, because that expansion is the entire reason this feature exists.
class StructureParserTest {

    private static JsonObject json(String raw) {
        return JsonParser.parseString(raw).getAsJsonObject();
    }

    private static StructureDefinition parse(String name, String raw) {
        return StructureParser.parse(name, json(raw));
    }

    @Test
    void twoFieldsReachEveryVillage() {
        StructureDefinition definition = parse("bakery", """
                { "structure": "bakery.nbt", "weight": 5 }
                """);

        assertEquals("datadrivenvillagers:bakery", definition.id().toString());
        assertEquals(5, definition.weight());
        assertEquals(GroundKind.RIGID, definition.ground());

        List<String> pools = definition.targetPools().stream().map(Object::toString).toList();
        assertEquals(5, pools.size());
        assertTrue(pools.contains("minecraft:village/plains/houses"));
        assertTrue(pools.contains("minecraft:village/taiga/houses"));
    }

    @Test
    void narrowsToNamedVillagesAndPool() {
        StructureDefinition definition = parse("well", """
                { "structure": "well.nbt", "villages": ["desert"], "pool": "decor" }
                """);

        assertEquals(List.of("minecraft:village/desert/decor"),
                definition.targetPools().stream().map(Object::toString).toList());
    }

    /// The escape hatch, for a pool no shorthand of ours can name.
    @Test
    void takesPoolIdsWrittenOut() {
        StructureDefinition definition = parse("odd", """
                { "structure": "odd.nbt", "pools": ["somemod:village/glass/houses"] }
                """);

        assertEquals(List.of("somemod:village/glass/houses"),
                definition.targetPools().stream().map(Object::toString).toList());
    }

    /// Both forms at once would make the file say two things about where the building goes.
    @Test
    void rejectsTheShorthandAndTheLongFormTogether() {
        DefinitionParseException e = assertThrows(DefinitionParseException.class, () -> parse("both", """
                { "structure": "a.nbt", "pools": ["minecraft:village/plains/houses"], "villages": ["plains"] }
                """));
        assertTrue(e.getMessage().contains("pools"));
    }

    /// A name without the extension is more likely an identifier the author expected to resolve elsewhere.
    @Test
    void rejectsAStructureThatIsNotAFileName() {
        assertThrows(DefinitionParseException.class, () -> parse("x", """
                { "structure": "mypack:bakery" }
                """));
    }

    @Test
    void rejectsAMissingStructureField() {
        DefinitionParseException e = assertThrows(DefinitionParseException.class, () -> parse("x", "{}"));
        assertTrue(e.getMessage().contains("structure"));
    }

    /// The other way to say what the building is: a block, and the mod draws the plot around it.
    @Test
    void takesAWorkstationInsteadOfAFile() {
        StructureDefinition definition = parse("bakery", """
                { "workstation": "minecraft:campfire", "weight": 3 }
                """);

        assertTrue(definition.generated());
        assertTrue(definition.file().isEmpty());
        assertEquals("minecraft:campfire", definition.workstation().get().toString());
        assertEquals("datadrivenvillagers:bakery/desert",
                definition.templateId(Identifier.of("minecraft", "village/desert/houses")).toString());
        assertEquals("datadrivenvillagers:bakery/plains", definition.firstTemplateId().toString());
    }

    /// One field, one meaning, the rule every format in this mod follows.
    @Test
    void rejectsAFileAndAWorkstationTogether() {
        DefinitionParseException e = assertThrows(DefinitionParseException.class, () -> parse("x", """
                { "structure": "x.nbt", "workstation": "minecraft:campfire" }
                """));
        assertTrue(e.getMessage().contains("one or the other"));
    }

    @Test
    void anAuthorsFileIsOneTemplateForEveryVillage() {
        StructureDefinition definition = parse("bakery", """
                { "structure": "bakery.nbt" }
                """);
        assertEquals("datadrivenvillagers:bakery",
                definition.templateId(Identifier.of("minecraft", "village/desert/houses")).toString());
    }

    @Test
    void rejectsAnUnknownVillageOrPool() {
        assertThrows(DefinitionParseException.class, () -> parse("x", """
                { "structure": "a.nbt", "villages": ["mushroom"] }
                """));
        assertThrows(DefinitionParseException.class, () -> parse("y", """
                { "structure": "a.nbt", "pool": "terminators" }
                """));
    }

    @Test
    void rejectsAWeightBelowOne() {
        assertThrows(DefinitionParseException.class, () -> parse("x", """
                { "structure": "a.nbt", "weight": 0 }
                """));
    }

    @Test
    void readsGroundAndProcessors() {
        StructureDefinition definition = parse("path", """
                {
                  "structure": "path.nbt",
                  "pool": "streets",
                  "ground": "terrain",
                  "processors": "minecraft:mossify_10_percent"
                }
                """);

        assertEquals(GroundKind.TERRAIN, definition.ground());
        assertEquals("minecraft:mossify_10_percent", definition.processors().orElseThrow().toString());
    }

    /// Same rule as a texture; this nbt is read straight into a structure template with no other check.
    @Test
    void rejectsAnNbtThatLeavesTheFolder() {
        // Doubled, because that is how a backslash is written inside json; the parsed value holds one.
        String bs = String.valueOf((char) 92).repeat(2);
        assertThrows(DefinitionParseException.class, () -> parse("evil", """
                { "structure": "%s" }
                """.formatted(".." + bs + ".." + bs + "secret.nbt")));
    }
}
