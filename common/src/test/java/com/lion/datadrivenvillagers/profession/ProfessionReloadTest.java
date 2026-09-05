package com.lion.datadrivenvillagers.profession;

import com.google.gson.JsonParser;
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

        List<ProfessionRegistry.LoadError> errors = List.of(
                new ProfessionRegistry.LoadError("zz_test_custom.json", "unknown activity \"play\""));
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
        List<ProfessionRegistry.LoadError> errors = List.of(
                new ProfessionRegistry.LoadError("malformed.json", "End of input"));

        assertTrue(ProfessionLoader.keptDespiteRejection(previousOf(night), errors).isEmpty());
    }

    /// An override is keyed by the vanilla id, so matching has to go by file name, not by key.
    @Test
    void aRejectedOverrideIsMatchedByItsFileName() {
        ProfessionDefinition farmer = parse("zz_test_farmer_night", """
                { "overrides": "minecraft:farmer", "schedule": "night" }
                """);
        List<ProfessionRegistry.LoadError> errors = List.of(
                new ProfessionRegistry.LoadError("zz_test_farmer_night.json", "broken"));

        Map<Identifier, ProfessionDefinition> kept = ProfessionLoader.keptDespiteRejection(previousOf(farmer), errors);
        assertTrue(kept.containsKey(Identifier.of("minecraft", "farmer")));
    }

    @Test
    void theRejectionSaysWhatKeepsRunning() {
        ProfessionDefinition custom = parse("zz_test_custom", """
                { "workstation": "minecraft:lantern" }
                """);
        Map<Identifier, ProfessionDefinition> kept = previousOf(custom);
        ProfessionRegistry.LoadError edited = new ProfessionRegistry.LoadError("zz_test_custom.json", "unknown activity \"play\"");
        ProfessionRegistry.LoadError fresh = new ProfessionRegistry.LoadError("malformed.json", "End of input");

        assertTrue(ProfessionLoader.rejectionDetail(edited, kept).contains("stays in effect"));
        assertEquals("End of input", ProfessionLoader.rejectionDetail(fresh, kept));
    }
}
