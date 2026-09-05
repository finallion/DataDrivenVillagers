package com.lion.datadrivenvillagers.profession;

import com.google.gson.JsonParser;
import com.lion.datadrivenvillagers.DefinitionParseException;

import net.minecraft.util.Identifier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttackTest {

    private static ProfessionDefinition parse(String raw) {
        return ProfessionParser.parse("guard", JsonParser.parseString(raw).getAsJsonObject());
    }

    @Test
    void absentByDefault() {
        assertTrue(parse("""
                { "workstation": "minecraft:lantern" }
                """).attack().isEmpty());
    }

    @Test
    void targetsAloneTakeTheDefaults() {
        Attack attack = parse("""
                { "workstation": "minecraft:lantern", "attacks": ["minecraft:zombie", { "entity": "minecraft:skeleton", "distance": 12 }] }
                """).attack().orElseThrow();
        assertEquals(2, attack.targets().size());
        assertEquals(EntityRange.DEFAULT_DISTANCE, attack.of(Identifier.ofVanilla("zombie")).orElseThrow().distance());
        assertEquals(12, attack.of(Identifier.ofVanilla("skeleton")).orElseThrow().distance());
        assertEquals(Attack.DEFAULT_DAMAGE, attack.damage());
        assertEquals(Attack.DEFAULT_COOLDOWN, attack.cooldown());
    }

    @Test
    void settingsChangeDamageAndCooldown() {
        Attack attack = parse("""
                { "workstation": "minecraft:lantern", "attacks": "minecraft:zombie", "attack": { "damage": 4.5, "cooldown": 10 } }
                """).attack().orElseThrow();
        assertEquals(4.5, attack.damage());
        assertEquals(10, attack.cooldown());
    }

    @Test
    void settingsWithoutTargetsAreRejected() {
        DefinitionParseException e = assertThrows(DefinitionParseException.class, () -> parse("""
                { "workstation": "minecraft:lantern", "attack": { "damage": 4 } }
                """));
        assertTrue(e.getMessage().contains("names nothing"), e.getMessage());
    }

    @Test
    void badNumbersAreRejectedWithTheFieldNamed() {
        DefinitionParseException zero = assertThrows(DefinitionParseException.class, () -> parse("""
                { "workstation": "minecraft:lantern", "attacks": "minecraft:zombie", "attack": { "damage": 0 } }
                """));
        assertTrue(zero.getMessage().contains("attack.damage"), zero.getMessage());

        DefinitionParseException text = assertThrows(DefinitionParseException.class, () -> parse("""
                { "workstation": "minecraft:lantern", "attacks": "minecraft:zombie", "attack": { "cooldown": "fast" } }
                """));
        assertTrue(text.getMessage().contains("cooldown"), text.getMessage());
    }

    @Test
    void attacksWorkOnAnOverrideToo() {
        ProfessionDefinition definition = parse("""
                { "overrides": "minecraft:armorer", "attacks": "minecraft:zombie" }
                """);
        assertTrue(definition.attack().isPresent(), "read per villager, so an override carries it like the plan");
    }

    @Test
    void healthIsOptionalAndPositive() {
        assertTrue(parse("""
                { "workstation": "minecraft:lantern" }
                """).health().isEmpty(), "absent means vanilla's 20, never written down twice");
        assertEquals(40.0, parse("""
                { "workstation": "minecraft:lantern", "health": 40 }
                """).health().orElseThrow());
        DefinitionParseException e = assertThrows(DefinitionParseException.class, () -> parse("""
                { "workstation": "minecraft:lantern", "health": -5 }
                """));
        assertTrue(e.getMessage().contains("health"), e.getMessage());
    }
}
