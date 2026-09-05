package com.lion.datadrivenvillagers.profession;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lion.datadrivenvillagers.DefinitionParseException;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Covers the day plan format. Held to the same rules as the three parsers beside it: no registry, no
/// running game, so every rule a pack author can trip over is checkable here.
class ScheduleParserTest {

    private static Optional<ScheduleDefinition> parse(String raw) {
        JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
        return ScheduleParser.parse(root, "schedule");
    }

    private static ScheduleDefinition present(String raw) {
        return parse(raw).orElseThrow();
    }

    @Test
    void anAbsentFieldMeansVanillaDecides() {
        assertTrue(parse("{}").isEmpty());
        assertTrue(parse("{ \"schedule\": null }").isEmpty());
    }

    @Test
    void namesAPreset() {
        ScheduleDefinition night = present("{ \"schedule\": \"night\" }");
        assertEquals("night", night.name());
        assertTrue(night.isPreset());
    }

    @Test
    void theDefaultPresetIsVanillasOwnPlan() {
        ScheduleDefinition plan = present("{ \"schedule\": \"default\" }");
        assertEquals("10 idle, 2000 work, 9000 meet, 11000 idle, 12000 rest", plan.describe());
    }

    /// The whole reason the feature exists, and the one claim `/ddv why` makes about a plan without a
    /// villager to look at.
    @Test
    void theNightPresetWorksInTheDarkAndTheDefaultDoesNot() {
        assertTrue(present("{ \"schedule\": \"night\" }").worksAtNight());
        assertFalse(present("{ \"schedule\": \"default\" }").worksAtNight());
    }

    /// The night plan is the vanilla one rotated by half a day, not a second set of numbers. Checked
    /// against every tick of the day rather than against the five entries, because the interesting
    /// part is the wrap around at midnight, which is where a rotation written by hand goes wrong.
    @Test
    void theNightPresetIsTheDefaultHalfADayLater() {
        for (int time = 0; time < ScheduleParser.DAY_LENGTH; time++) {
            assertEquals(ScheduleParser.DEFAULT.activityAt((time + 12000) % ScheduleParser.DAY_LENGTH),
                    ScheduleParser.NIGHT.activityAt(time),
                    "tick " + time);
        }
    }

    /// Before the first entry of the day the plan is still under the last one of the previous day,
    /// which is what vanilla's own lookup does and the reason a plan needs no entry at tick 0.
    @Test
    void theLastEntryOfTheDayCarriesIntoTheNext() {
        assertEquals(ScheduleActivity.REST, ScheduleParser.DEFAULT.activityAt(0));
        assertEquals(ScheduleActivity.REST, ScheduleParser.DEFAULT.activityAt(9));
        assertEquals(ScheduleActivity.IDLE, ScheduleParser.DEFAULT.activityAt(10));
    }

    @Test
    void rejectsAnUnknownPresetAndSaysWhatThereIs() {
        DefinitionParseException thrown = assertThrows(DefinitionParseException.class,
                () -> parse("{ \"schedule\": \"nocturnal\" }"));
        assertTrue(thrown.getMessage().contains("night"), thrown.getMessage());
    }

    @Test
    void readsEntriesWrittenOut() {
        ScheduleDefinition plan = present("""
                {
                  "schedule": [
                    { "time": 13000, "activity": "work" },
                    { "time": 1000, "activity": "rest" }
                  ]
                }
                """);
        assertEquals(ScheduleDefinition.CUSTOM, plan.name());
        assertFalse(plan.isPreset());
        assertEquals(2, plan.entries().size());
        assertTrue(plan.worksAtNight());
    }

    /// Sorted on the way in, so two files that say the same thing in a different order are the same
    /// definition. A reload compares definitions to decide what to report, and order alone must not
    /// make it claim a change.
    @Test
    void sortsEntriesByTime() {
        ScheduleDefinition plan = present("""
                {
                  "schedule": [
                    { "time": 13000, "activity": "work" },
                    { "time": 1000, "activity": "rest" }
                  ]
                }
                """);
        assertEquals("1000 rest, 13000 work", plan.describe());
        assertEquals(plan, present("""
                {
                  "schedule": [
                    { "time": 1000, "activity": "rest" },
                    { "time": 13000, "activity": "work" }
                  ]
                }
                """));
    }

    @Test
    void aPresetAndTheSameEntriesWrittenOutAreNotTheSameDefinition() {
        // Only the name differs, and it exists so a report can say "night" instead of five numbers.
        assertNotEquals(ScheduleParser.NIGHT, present("""
                {
                  "schedule": [
                    { "time": 0, "activity": "rest" },
                    { "time": 12010, "activity": "idle" },
                    { "time": 14000, "activity": "work" },
                    { "time": 21000, "activity": "meet" },
                    { "time": 23000, "activity": "idle" }
                  ]
                }
                """));
    }

    @Test
    void rejectsTwoEntriesAtTheSameTime() {
        DefinitionParseException thrown = assertThrows(DefinitionParseException.class, () -> parse("""
                {
                  "schedule": [
                    { "time": 1000, "activity": "rest" },
                    { "time": 1000, "activity": "work" }
                  ]
                }
                """));
        assertTrue(thrown.getMessage().contains("1000"), thrown.getMessage());
    }

    /// A day is 24000 ticks. Wrapping 25000 into 1000 silently would be guessing at what an author
    /// meant, and the guess is nearly always wrong: the number is usually a misunderstanding of the
    /// clock rather than a deliberate second lap.
    @Test
    void rejectsATimeOutsideTheDay() {
        assertThrows(DefinitionParseException.class,
                () -> parse("{ \"schedule\": [ { \"time\": 24000, \"activity\": \"work\" } ] }"));
        assertThrows(DefinitionParseException.class,
                () -> parse("{ \"schedule\": [ { \"time\": -1, \"activity\": \"work\" } ] }"));
    }

    /// An activity a villager has no task list for leaves the brain with nothing to run, and a
    /// villager standing still looks exactly like a broken mod. Rejected with the four that work.
    @Test
    void rejectsAnActivityAVillagerCannotRunOnAClock() {
        DefinitionParseException thrown = assertThrows(DefinitionParseException.class,
                () -> parse("{ \"schedule\": [ { \"time\": 0, \"activity\": \"play\" } ] }"));
        assertTrue(thrown.getMessage().contains("work"), thrown.getMessage());

        assertThrows(DefinitionParseException.class,
                () -> parse("{ \"schedule\": [ { \"time\": 0, \"activity\": \"panic\" } ] }"));
    }

    @Test
    void rejectsAnEmptyPlan() {
        assertThrows(DefinitionParseException.class, () -> parse("{ \"schedule\": [] }"));
    }

    @Test
    void rejectsAnEntryMissingEitherHalf() {
        assertThrows(DefinitionParseException.class,
                () -> parse("{ \"schedule\": [ { \"time\": 0 } ] }"));
        assertThrows(DefinitionParseException.class,
                () -> parse("{ \"schedule\": [ { \"activity\": \"work\" } ] }"));
        assertThrows(DefinitionParseException.class,
                () -> parse("{ \"schedule\": [ \"work\" ] }"));
    }
}
