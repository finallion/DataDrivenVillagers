package com.lion.datadrivenvillagers.profession;

import com.google.gson.JsonParser;
import com.lion.datadrivenvillagers.LoadError;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfessionReloadTest {

    private static ProfessionDefinition parse(String name, String raw) {
        return ProfessionParser.parse(name, JsonParser.parseString(raw).getAsJsonObject());
    }

    private static Map<Identifier, ProfessionDefinition> previousOf(ProfessionDefinition... definitions) {
        Map<Identifier, ProfessionDefinition> previous = new LinkedHashMap<>();
        for (ProfessionDefinition definition : definitions) {
            previous.put(definition.target(), definition);
        }
        return previous;
    }

    @Test
    void aRejectedFileKeepsItsLastGoodDefinition() {
        ProfessionDefinition custom = parse("zz_test_custom", """
                { "workstation": "minecraft:lantern", "schedule": [ { "time": 0, "activity": "work" } ] }
                """);
        ProfessionDefinition night = parse("zz_test_night", """
                { "workstation": "minecraft:bookshelf", "schedule": "night" }
                """);
        Map<Identifier, ProfessionDefinition> previous = previousOf(custom, night);

        List<LoadError> errors = List.of(
                new LoadError("zz_test_custom.json", "unknown activity \"play\""));
        Map<Identifier, ProfessionDefinition> kept = ProfessionLoader.keptDespiteRejection(previous, errors);

        assertEquals(1, kept.size());
        assertTrue(kept.containsKey(custom.target()));
        assertFalse(kept.containsKey(night.target()), "a file that still parses is not 'kept', it is handled normally");
    }

    @Test
    void aFileThatNeverLoadedIsNotKept() {
        ProfessionDefinition night = parse("zz_test_night", """
                { "workstation": "minecraft:bookshelf" }
                """);
        List<LoadError> errors = List.of(
                new LoadError("malformed.json", "End of input"));

        assertTrue(ProfessionLoader.keptDespiteRejection(previousOf(night), errors).isEmpty());
    }

    /// An override is keyed by the vanilla id, so matching has to go by file name, not by key.
    @Test
    void aRejectedOverrideIsMatchedByItsFileName() {
        ProfessionDefinition farmer = parse("zz_test_farmer_night", """
                { "overrides": "minecraft:farmer", "schedule": "night" }
                """);
        List<LoadError> errors = List.of(
                new LoadError("zz_test_farmer_night.json", "broken"));

        Map<Identifier, ProfessionDefinition> kept = ProfessionLoader.keptDespiteRejection(previousOf(farmer), errors);
        assertTrue(kept.containsKey(Identifier.of("minecraft", "farmer")));
    }

    @Test
    void theRejectionSaysWhatKeepsRunning() {
        ProfessionDefinition custom = parse("zz_test_custom", """
                { "workstation": "minecraft:lantern" }
                """);
        Map<Identifier, ProfessionDefinition> kept = previousOf(custom);
        LoadError edited = new LoadError("zz_test_custom.json", "unknown activity \"play\"");
        LoadError fresh = new LoadError("malformed.json", "End of input");

        assertTrue(ProfessionLoader.rejectionDetail(edited, kept).contains("stays in effect"));
        assertEquals("End of input", ProfessionLoader.rejectionDetail(fresh, kept));
    }

    @Test
    void allowNaturalBlockAloneNeedsARestartOnAProfession() {
        ProfessionDefinition before = parse("zz_test_custom", """
                { "workstation": "minecraft:lantern" }
                """);
        ProfessionDefinition after = parse("zz_test_custom", """
                { "workstation": "minecraft:lantern", "allow_natural_block": true }
                """);

        assertTrue(ProfessionLoader.frozenFields(before, after).contains("allow_natural_block"));
    }

    @Test
    void allowNaturalBlockAloneNeedsNoRestartOnAnOverride() {
        ProfessionDefinition before = parse("zz_test_farmer_night", """
                { "overrides": "minecraft:farmer" }
                """);
        ProfessionDefinition after = parse("zz_test_farmer_night", """
                { "overrides": "minecraft:farmer", "allow_natural_block": true }
                """);

        assertTrue(ProfessionLoader.frozenFields(before, after).isEmpty());
    }

    @Test
    void isNaturalBlockMatchesTheFixedList() {
        assertTrue(ProfessionLoader.isNaturalBlock(Identifier.ofVanilla("stone")));
        assertFalse(ProfessionLoader.isNaturalBlock(Identifier.ofVanilla("crafting_table")));
    }

    @Test
    void isStructureBulkBlockMatchesTheFixedList() {
        assertTrue(ProfessionLoader.isStructureBulkBlock(Identifier.ofVanilla("cobblestone")));
        assertTrue(ProfessionLoader.isStructureBulkBlock(Identifier.ofVanilla("stone_bricks")));
        assertFalse(ProfessionLoader.isStructureBulkBlock(Identifier.ofVanilla("crafting_table")));
    }
}
