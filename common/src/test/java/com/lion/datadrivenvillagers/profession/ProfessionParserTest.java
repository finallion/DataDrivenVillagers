package com.lion.datadrivenvillagers.profession;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lion.datadrivenvillagers.DefinitionParseException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfessionParserTest {

    private static JsonObject json(String raw) {
        return JsonParser.parseString(raw).getAsJsonObject();
    }

    private static ProfessionDefinition parse(String name, String raw) {
        return ProfessionParser.parse(name, json(raw));
    }

    @Test
    void readsAFullDefinition() {
        ProfessionDefinition definition = parse("baker", """
                {
                  "workstation": "minecraft:smoker",
                  "display_name": "Baker",
                  "texture": "baker.png",
                  "hat": "partial",
                  "work_sound": "minecraft:entity.villager.work_farmer",
                  "gatherable_items": ["minecraft:wheat"],
                  "secondary_job_sites": ["minecraft:cake"],
                  "ticket_count": 2,
                  "search_distance": 3
                }
                """);

        assertEquals("datadrivenvillagers:baker", definition.id().toString());
        assertEquals(1, definition.workstations().size());
        assertEquals("Baker", definition.displayName().orElseThrow());
        assertEquals(HatKind.PARTIAL, definition.hat());
        assertEquals(2, definition.ticketCount());
        assertEquals(3, definition.searchDistance());
        assertEquals(1, definition.gatherable().size());
        assertEquals(1, definition.secondarySites().size());
    }

    @Test
    void acceptsAListOfWorkstations() {
        ProfessionDefinition definition = parse("smith", """
                { "workstation": ["minecraft:anvil", "minecraft:smithing_table"] }
                """);
        assertEquals(2, definition.workstations().size());
    }

    @Test
    void appliesDefaults() {
        ProfessionDefinition definition = parse("plain", """
                { "workstation": "minecraft:smoker" }
                """);
        assertEquals(HatKind.NONE, definition.hat());
        assertEquals(1, definition.ticketCount());
        assertEquals(1, definition.searchDistance());
        assertTrue(definition.displayName().isEmpty());
        assertTrue(definition.texture().isEmpty());
        assertTrue(definition.textureFile().isEmpty());
    }

    /// A bare file name is a png next to the json, anything with a colon or slash is a resource pack
    /// identifier. Getting this split wrong would silently show the wrong texture.
    @Test
    void separatesTextureFileFromTextureIdentifier() {
        assertEquals("baker.png", parse("a", """
                { "workstation": "minecraft:smoker", "texture": "baker.png" }
                """).textureFile().orElseThrow());

        assertEquals("mypack:textures/entity/villager/profession/baker.png", parse("b", """
                { "workstation": "minecraft:smoker", "texture": "mypack:textures/entity/villager/profession/baker.png" }
                """).texture().orElseThrow().toString());
    }

    @Test
    void rejectsMissingWorkstation() {
        DefinitionParseException e = assertThrows(DefinitionParseException.class, () -> parse("x", "{}"));
        assertTrue(e.getMessage().contains("workstation"));
    }

    @Test
    void rejectsEmptyWorkstationList() {
        assertThrows(DefinitionParseException.class, () -> parse("x", """
                { "workstation": [] }
                """));
    }

    @Test
    void rejectsAnUnknownHat() {
        assertThrows(DefinitionParseException.class, () -> parse("x", """
                { "workstation": "minecraft:smoker", "hat": "sombrero" }
                """));
    }

    @Test
    void rejectsAMalformedIdentifier() {
        assertThrows(DefinitionParseException.class, () -> parse("x", """
                { "workstation": "Not An Id" }
                """));
    }

    @Test
    void rejectsAFileNameThatCannotBeAnId() {
        assertThrows(DefinitionParseException.class, () -> parse("Bad Name", """
                { "workstation": "minecraft:smoker" }
                """));
    }

    @Test
    void rejectsANonPositiveTicketCount() {
        assertThrows(DefinitionParseException.class, () -> parse("x", """
                { "workstation": "minecraft:smoker", "ticket_count": 0 }
                """));
    }

    @Test
    void rejectsAWronglyTypedField() {
        assertThrows(DefinitionParseException.class, () -> parse("x", """
                { "workstation": 5 }
                """));
    }

    @Test
    void readsAGiftLootTable() {
        ProfessionDefinition definition = parse("baker", """
                {
                  "workstation": "minecraft:campfire",
                  "gift": "datadrivenvillagers:gameplay/hero_of_the_village/baker_gift"
                }
                """);

        assertEquals("datadrivenvillagers:gameplay/hero_of_the_village/baker_gift",
                definition.gift().orElseThrow().toString());
    }

    /// Absent rather than derived from the id: vanilla then keeps handing out the unemployed gift
    /// instead of missing a loot table nobody wrote.
    @Test
    void leavesTheGiftAbsentWhenUnset() {
        assertTrue(parse("baker", """
                { "workstation": "minecraft:campfire" }
                """).gift().isEmpty());
    }

    /// An override keeps its own identity for the texture and points at somebody else's profession
    /// for everything the game asks about. Mixing those two up would either serve the wrong image or
    /// shadow a vanilla texture a resource pack is also writing to.
    @Test
    void anOverrideKeepsItsOwnIdButTargetsAnother() {
        ProfessionDefinition definition = parse("my_farmer", """
                { "overrides": "minecraft:farmer", "texture": "my_farmer.png" }
                """);

        assertTrue(definition.isOverride());
        assertEquals("datadrivenvillagers:my_farmer", definition.id().toString());
        assertEquals("minecraft:farmer", definition.target().toString());
        assertEquals("datadrivenvillagers:textures/entity/villager/profession/my_farmer.png",
                definition.vanillaTextureId("villager").toString());
    }

    @Test
    void anOverrideNeedsNoWorkstation() {
        assertTrue(parse("gift_only", """
                { "overrides": "minecraft:farmer", "gift": "mypack:some_table" }
                """).workstations().isEmpty());
    }

    /// The two words mean different things and must never be interchangeable: one creates a job site,
    /// the other hands blocks to one that exists.
    @Test
    void rejectsWorkstationTogetherWithOverrides() {
        DefinitionParseException e = assertThrows(DefinitionParseException.class, () -> parse("x", """
                { "overrides": "minecraft:farmer", "workstation": "minecraft:barrel" }
                """));
        assertTrue(e.getMessage().contains("add_workstations"));
    }

    @Test
    void rejectsAddWorkstationsWithoutOverrides() {
        assertThrows(DefinitionParseException.class, () -> parse("x", """
                { "workstation": "minecraft:campfire", "add_workstations": "minecraft:barrel" }
                """));
    }

    @Test
    void readsBlocksAddedToAnOverriddenProfession() {
        ProfessionDefinition definition = parse("farmer_plus", """
                { "overrides": "minecraft:farmer", "add_workstations": ["minecraft:hay_block"] }
                """);
        assertEquals(1, definition.addWorkstations().size());
    }

    /// The example is the first thing every user sees, so it has to parse and it has to name a
    /// texture. It shipped without one, which meant the first villager anybody ever saw wore the
    /// missing texture while the readme beside it explained how textures work.
    @Test
    void theShippedExampleParsesAndNamesATextureFile() {
        ProfessionDefinition definition = parse("example_baker", ExampleProfession.EXAMPLE);

        assertEquals("example_baker.png", definition.textureFile().orElseThrow(),
                "the shipped example must point at a png next to it");
        assertTrue(definition.texture().isEmpty(),
                "a bare file name must not be read as a resource pack identifier");
    }

    /// The file is read from disk and sent to every player who joins, and an operator can write this
    /// field over the network through the editor. On Windows a backslash in it is a path separator,
    /// so `..` backslash `..` leaves the professions folder; refused on every platform so a pack
    /// behaves the same everywhere.
    @Test
    void rejectsATextureThatLeavesTheFolder() {
        // Doubled, because that is how a backslash is written inside json; the parsed value holds one.
        String bs = String.valueOf((char) 92).repeat(2);
        assertThrows(DefinitionParseException.class, () -> parse("evil", """
                { "workstation": "minecraft:campfire", "texture": "%s" }
                """.formatted(".." + bs + ".." + bs + "server.properties")));
        assertThrows(DefinitionParseException.class, () -> parse("evil", """
                { "workstation": "minecraft:campfire", "zombie_texture": "%s" }
                """.formatted(".." + bs + "x.png")));
        assertThrows(DefinitionParseException.class, () -> parse("evil", """
                { "workstation": "minecraft:campfire", "texture": ".." }
                """));
    }

    /// A name with a space is somebody's working pack; only paths are refused, not spellings.
    @Test
    void keepsAnOddButHarmlessTextureName() {
        assertEquals("my baker.png", parse("baker", """
                { "workstation": "minecraft:campfire", "texture": "my baker.png" }
                """).textureFile().orElseThrow());
    }
}
