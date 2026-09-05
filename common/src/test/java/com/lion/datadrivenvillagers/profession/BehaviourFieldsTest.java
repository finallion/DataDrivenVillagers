package com.lion.datadrivenvillagers.profession;

import com.google.gson.JsonParser;
import com.lion.datadrivenvillagers.DefinitionParseException;

import net.minecraft.util.Identifier;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BehaviourFieldsTest {

    private static ProfessionDefinition parse(String name, String raw) {
        return ProfessionParser.parse(name, JsonParser.parseString(raw).getAsJsonObject());
    }

    @Test
    void defaultsAreVanillasPlainVillager() {
        ProfessionDefinition definition = parse("baker", """
                { "workstation": "minecraft:smoker" }
                """);
        assertEquals(WorkBehaviour.STATION, definition.workBehaviour());
        assertEquals(Fears.VANILLA, definition.fears());
        assertTrue(definition.villages().isEmpty());
        assertTrue(definition.zombieTexture().isEmpty());
        assertTrue(definition.zombieTextureFile().isEmpty());
    }

    @Test
    void farmFillsInTheFarmersItemsAndFarmland() {
        ProfessionDefinition definition = parse("herbalist", """
                { "workstation": "minecraft:flower_pot", "work_behaviour": "farm" }
                """);
        assertEquals(WorkBehaviour.FARM, definition.workBehaviour());
        assertEquals(ProfessionParser.FARM_GATHERABLE, definition.gatherable());
        assertEquals(List.of(new Identifier("farmland")), definition.secondarySites());
    }

    @Test
    void farmKeepsWhatTheFileNamesItself() {
        ProfessionDefinition definition = parse("herbalist", """
                {
                  "workstation": "minecraft:flower_pot",
                  "work_behaviour": "FARM",
                  "gatherable_items": "minecraft:carrot"
                }
                """);
        assertEquals(List.of(new Identifier("carrot")), definition.gatherable());
        assertEquals(List.of(new Identifier("farmland")), definition.secondarySites(),
                "only the list that was left out is filled in");
    }

    @Test
    void farmOnAnOverrideIsRejectedUnlessItIsTheFarmer() {
        DefinitionParseException e = assertThrows(DefinitionParseException.class, () -> parse("night_librarian", """
                { "overrides": "minecraft:librarian", "work_behaviour": "farm" }
                """));
        assertTrue(e.getMessage().contains("farmland"), e.getMessage());

        ProfessionDefinition farmer = parse("night_farmer", """
                { "overrides": "minecraft:farmer", "work_behaviour": "farm", "schedule": "night" }
                """);
        assertEquals(WorkBehaviour.FARM, farmer.workBehaviour());
        assertTrue(farmer.gatherable().isEmpty(), "an override never reads gatherable_items, so none are filled in");
    }

    @Test
    void unknownWorkBehaviourListsTheChoices() {
        DefinitionParseException e = assertThrows(DefinitionParseException.class, () -> parse("x", """
                { "workstation": "minecraft:smoker", "work_behaviour": "mine" }
                """));
        assertTrue(e.getMessage().contains("station") && e.getMessage().contains("farm"), e.getMessage());
    }

    @Test
    void fleesFromTakesIdsAndObjects() {
        ProfessionDefinition definition = parse("x", """
                {
                  "workstation": "minecraft:smoker",
                  "flees_from": ["minecraft:creeper", { "entity": "minecraft:wolf", "distance": 12 }]
                }
                """);
        assertEquals(List.of(
                new EntityRange(new Identifier("creeper"), EntityRange.DEFAULT_DISTANCE),
                new EntityRange(new Identifier("wolf"), 12)), definition.fears().entries());
        assertFalse(definition.fears().replacesVanilla());
    }

    @Test
    void fleesFromTakesASingleId() {
        ProfessionDefinition definition = parse("x", """
                { "workstation": "minecraft:smoker", "flees_from": "minecraft:creeper" }
                """);
        assertEquals(1, definition.fears().entries().size());
    }

    @Test
    void fleesFromRefusesTagsAndObjectsWithoutAnEntity() {
        DefinitionParseException tag = assertThrows(DefinitionParseException.class, () -> parse("x", """
                { "workstation": "minecraft:smoker", "flees_from": "#minecraft:raiders" }
                """));
        assertTrue(tag.getMessage().contains("tag"), tag.getMessage());

        DefinitionParseException missing = assertThrows(DefinitionParseException.class, () -> parse("x", """
                { "workstation": "minecraft:smoker", "flees_from": [{ "distance": 4 }] }
                """));
        assertTrue(missing.getMessage().contains("flees_from[0]"), missing.getMessage());
    }

    @Test
    void villagesAreVillagerTypeIds() {
        ProfessionDefinition definition = parse("x", """
                { "workstation": "minecraft:smoker", "villages": ["desert", "datadrivenvillagers:swamp"] }
                """);
        assertEquals(List.of(new Identifier("desert"), Identifier.of("datadrivenvillagers", "swamp")),
                definition.villages());
    }

    @Test
    void zombieTextureFollowsTheSameFileOrIdRule() {
        ProfessionDefinition file = parse("x", """
                { "workstation": "minecraft:smoker", "texture": "x.png", "zombie_texture": "x_zombie.png" }
                """);
        assertEquals("x_zombie.png", file.zombieTextureFile().orElseThrow());
        assertEquals("x_zombie.png", file.textureFileFor("zombie_villager").orElseThrow());
        assertEquals("x.png", file.textureFileFor("villager").orElseThrow());

        ProfessionDefinition id = parse("x", """
                { "workstation": "minecraft:smoker", "zombie_texture": "mypack:textures/entity/z.png" }
                """);
        assertEquals(Identifier.of("mypack", "textures/entity/z.png"), id.zombieTexture().orElseThrow());
        assertTrue(id.textureFileFor("zombie_villager").isEmpty());
    }

    @Test
    void withoutAZombieTextureTheZombieWearsTheVillagersImage() {
        ProfessionDefinition definition = parse("x", """
                { "workstation": "minecraft:smoker", "texture": "x.png" }
                """);
        assertEquals("x.png", definition.textureFileFor("zombie_villager").orElseThrow());
    }

    @Test
    void fleesOnlyFromReplacesVanillasListAndMayBeEmpty() {
        ProfessionDefinition only = parse("x", """
                { "workstation": "minecraft:smoker", "flees_only_from": ["minecraft:creeper"] }
                """);
        assertTrue(only.fears().replacesVanilla());
        assertEquals(1, only.fears().entries().size());

        ProfessionDefinition brave = parse("x", """
                { "workstation": "minecraft:smoker", "flees_only_from": [] }
                """);
        assertTrue(brave.fears().replacesVanilla());
        assertTrue(brave.fears().entries().isEmpty());
        assertTrue(brave.fears().isSet(), "an empty replacing list is a statement, not an omission");
        assertTrue(brave.fears().describe().contains("nothing"));
    }

    @Test
    void fleesFromAndFleesOnlyFromTogetherAreRejected() {
        DefinitionParseException e = assertThrows(DefinitionParseException.class, () -> parse("x", """
                { "workstation": "minecraft:smoker", "flees_from": "minecraft:creeper", "flees_only_from": [] }
                """));
        assertTrue(e.getMessage().contains("one or the other"), e.getMessage());
    }

    @Test
    void behaviourFieldsNamesEveryFieldAnOverrideDoesRead() {
        ProfessionDefinition quiet = parse("looks_only", """
                { "overrides": "minecraft:nitwit", "texture": "x.png", "hat": "full" }
                """);
        assertTrue(ProfessionLoader.behaviourFields(quiet).isEmpty(),
                "texture and hat are the look, not the behaviour");

        ProfessionDefinition busy = parse("busy", """
                {
                  "overrides": "minecraft:nitwit",
                  "schedule": "night",
                  "flees_only_from": [],
                  "attacks": "minecraft:zombie",
                  "health": 40,
                  "villages": "minecraft:plains"
                }
                """);
        assertEquals(List.of("schedule", "flees_only_from", "attacks", "health", "villages"),
                ProfessionLoader.behaviourFields(busy));
    }

    /// `flees_from` and `flees_only_from` are two fields with one meaning each, and the verdict has
    /// to name the one the author wrote or they will search the wrong line.
    @Test
    void behaviourFieldsTellsTheTwoFearListsApart() {
        ProfessionDefinition adds = parse("adds", """
                { "overrides": "minecraft:farmer", "flees_from": "minecraft:creeper" }
                """);
        assertEquals(List.of("flees_from"), ProfessionLoader.behaviourFields(adds));

        ProfessionDefinition vanilla = parse("vanilla", """
                { "overrides": "minecraft:farmer", "texture": "x.png" }
                """);
        assertTrue(ProfessionLoader.behaviourFields(vanilla).isEmpty());
    }
}
