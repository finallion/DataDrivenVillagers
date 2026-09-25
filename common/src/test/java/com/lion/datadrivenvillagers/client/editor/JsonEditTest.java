package com.lion.datadrivenvillagers.client.editor;

import com.lion.datadrivenvillagers.profession.ProfessionDefinition;
import com.lion.datadrivenvillagers.profession.ProfessionParser;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonEditTest {

    private static JsonObject parse(String raw) {
        return JsonParser.parseString(raw).getAsJsonObject();
    }

    /// The promise the editor makes to a file it did not write.
    @Test
    void keepsWhatItHasNoWidgetFor() {
        JsonObject root = parse("""
                { "_comment": "mine", "workstation": "minecraft:calcite", "from_a_newer_version": 7 }
                """);

        JsonEdit.setText(root, "display_name", "Stonecutter");

        assertEquals("mine", JsonEdit.text(root, "_comment"));
        assertEquals("7", JsonEdit.number(root, "from_a_newer_version"));
        assertEquals("Stonecutter", JsonEdit.text(root, "display_name"));
    }

    @Test
    void blankTakesTheKeyOutRatherThanEmptyingIt() {
        JsonObject root = parse("""
                { "display_name": "Baker", "gift": "minecraft:gift", "attacks": ["minecraft:zombie"] }
                """);

        JsonEdit.setText(root, "display_name", "   ");
        JsonEdit.setList(root, "gift", "");
        JsonEdit.setRanges(root, "attacks", "");

        assertFalse(root.has("display_name"));
        assertFalse(root.has("gift"));
        assertFalse(root.has("attacks"));
    }

    /// A single value must round-trip as a bare string, not an array, or edits leave fingerprints on untouched files.
    @Test
    void oneIdStaysABareString() {
        JsonObject root = new JsonObject();

        JsonEdit.setList(root, "workstation", "minecraft:calcite");
        assertTrue(root.get("workstation").isJsonPrimitive());

        JsonEdit.setList(root, "workstation", "minecraft:calcite, minecraft:tuff");
        assertTrue(root.get("workstation").isJsonArray());
        assertEquals("minecraft:calcite, minecraft:tuff", JsonEdit.list(root, "workstation"));
    }

    @Test
    void readsBothShapesOfAList() {
        assertEquals("minecraft:calcite", JsonEdit.list(parse("""
                { "workstation": "minecraft:calcite" }
                """), "workstation"));
        assertEquals("minecraft:calcite, minecraft:tuff", JsonEdit.list(parse("""
                { "workstation": ["minecraft:calcite", "minecraft:tuff"] }
                """), "workstation"));
    }

    @Test
    void distancesSurviveBothWays() {
        JsonObject root = parse("""
                { "attacks": ["minecraft:skeleton", { "entity": "minecraft:zombie", "distance": 10 }] }
                """);
        assertEquals("minecraft:skeleton, minecraft:zombie@10", JsonEdit.ranges(root, "attacks"));

        JsonEdit.setRanges(root, "attacks", "minecraft:zombie@10, minecraft:skeleton");
        assertEquals("minecraft:zombie@10, minecraft:skeleton", JsonEdit.ranges(root, "attacks"));
    }

    @Test
    void planEntriesSurviveBothWays() {
        JsonObject root = parse("""
                { "schedule": [{ "time": 0, "activity": "rest" }, { "time": 14000, "activity": "work" }] }
                """);
        assertEquals("0=rest, 14000=work", JsonEdit.schedule(root, "schedule"));

        JsonEdit.setSchedule(root, "schedule", "0=rest, 12000=idle, 14000=work");
        assertEquals("0=rest, 12000=idle, 14000=work", JsonEdit.schedule(root, "schedule"));
    }

    /// Gson reads every number as a double; writing 3.0 back would make `ticket_count` look like a fraction.
    @Test
    void wholeNumbersDoNotGrowADecimalPoint() {
        JsonObject root = parse("""
                { "ticket_count": 3, "health": 40 }
                """);
        assertEquals("3", JsonEdit.number(root, "ticket_count"));
        assertEquals("40", JsonEdit.number(root, "health"));

        JsonEdit.setNumber(root, "search_distance", "48");
        assertEquals("48", JsonEdit.number(root, "search_distance"));
    }

    @Test
    void anEmptyFearListIsSomethingElseThanNoFearList() {
        JsonObject root = parse("""
                { "flees_only_from": [] }
                """);
        assertTrue(JsonEdit.has(root, "flees_only_from"));
        assertTrue(JsonEdit.isEmptyArray(root, "flees_only_from"));
        assertFalse(JsonEdit.isEmptyArray(parse("""
                { "flees_only_from": ["minecraft:creeper"] }
                """), "flees_only_from"));
    }

    /// A list field would otherwise lose everything before the comma the moment a suggestion is clicked.
    @Test
    void completionLooksAtTheLastValueOnly() {
        assertEquals("minecraft:calc", EditorFields.lastPart("minecraft:calc"));
        assertEquals("minecraft:tu", EditorFields.lastPart("minecraft:calcite, minecraft:tu"));
        assertEquals("", EditorFields.lastPart("minecraft:calcite, "));
    }

    /// Whatever the editor assembles must pass the same parser a hand-written file would meet.
    @Test
    void whatItBuildsIsAFileTheParserTakes() {
        JsonObject root = new JsonObject();
        JsonEdit.setList(root, "workstation", "minecraft:calcite, minecraft:tuff");
        JsonEdit.setText(root, "display_name", "Two Blocks");
        JsonEdit.setText(root, "hat", "none");
        JsonEdit.setNumber(root, "ticket_count", "3");
        JsonEdit.setRanges(root, "attacks", "minecraft:zombie@10");
        JsonEdit.setSchedule(root, "schedule", "0=rest, 14000=work");

        ProfessionDefinition definition = ProfessionParser.parse("two_blocks", root);

        assertEquals(2, definition.workstations().size());
        assertEquals(3, definition.ticketCount());
        assertEquals("Two Blocks", definition.displayName().orElseThrow());
        assertEquals(2, definition.schedule().orElseThrow().entries().size());
        assertEquals(1, definition.attack().orElseThrow().targets().size());
    }
}
