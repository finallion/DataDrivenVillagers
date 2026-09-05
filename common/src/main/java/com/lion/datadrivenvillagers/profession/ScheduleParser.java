package com.lion.datadrivenvillagers.profession;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lion.datadrivenvillagers.DefinitionParseException;
import com.lion.datadrivenvillagers.JsonFields;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/// Turns the `"schedule"` field into a {@link ScheduleDefinition}: a preset name, or the entries
/// written out. No registry access.
public final class ScheduleParser {

    public static final int DAY_LENGTH = 24000;

    private static final int HALF_DAY = DAY_LENGTH / 2;

    /// Vanilla's `Schedule.VILLAGER_DEFAULT` in 1.20.1. A preset so a reload can fall back to it
    /// when `"schedule"` is removed from a file.
    public static final ScheduleDefinition DEFAULT = new ScheduleDefinition("default", List.of(
            new ScheduleDefinition.Entry(10, ScheduleActivity.IDLE),
            new ScheduleDefinition.Entry(2000, ScheduleActivity.WORK),
            new ScheduleDefinition.Entry(9000, ScheduleActivity.MEET),
            new ScheduleDefinition.Entry(11000, ScheduleActivity.IDLE),
            new ScheduleDefinition.Entry(12000, ScheduleActivity.REST)));

    /// {@link #DEFAULT} shifted by half a day.
    public static final ScheduleDefinition NIGHT = shifted(DEFAULT, "night", HALF_DAY);

    public static final List<ScheduleDefinition> PRESETS = List.of(DEFAULT, NIGHT);

    private ScheduleParser() {
    }

    public static List<String> presetNames() {
        return PRESETS.stream().map(ScheduleDefinition::name).toList();
    }

    public static Optional<ScheduleDefinition> parse(JsonObject root, String field) {
        JsonElement element = root.get(field);
        if (element == null || element.isJsonNull()) {
            return Optional.empty();
        }
        if (element.isJsonArray()) {
            return Optional.of(entries(element.getAsJsonArray(), field));
        }
        return Optional.of(preset(JsonFields.asString(element, field)));
    }

    private static ScheduleDefinition preset(String raw) {
        String wanted = raw.toLowerCase(Locale.ROOT);
        for (ScheduleDefinition preset : PRESETS) {
            if (preset.name().equals(wanted)) {
                return preset;
            }
        }
        throw new DefinitionParseException("unknown schedule \"" + raw + "\", expected one of "
                + presetNames() + ", or a list of {\"time\": <0-" + (DAY_LENGTH - 1)
                + ">, \"activity\": <name>} written out");
    }

    /// Sorted, so two files with the same entries in a different order compare equal on a reload.
    private static ScheduleDefinition entries(JsonArray array, String field) {
        if (array.isEmpty()) {
            throw new DefinitionParseException("\"" + field + "\" is empty, a day plan needs at least "
                    + "one entry. Leave the field out to keep the vanilla plan.");
        }

        List<ScheduleDefinition.Entry> entries = new ArrayList<>();
        Set<Integer> times = new LinkedHashSet<>();
        for (int i = 0; i < array.size(); i++) {
            String where = field + "[" + i + "]";
            JsonElement element = array.get(i);
            if (!element.isJsonObject()) {
                throw new DefinitionParseException("\"" + where + "\" must be an object with "
                        + "\"time\" and \"activity\"");
            }

            JsonObject object = element.getAsJsonObject();
            int time = time(object, where);
            ScheduleActivity activity = ScheduleActivity.parse(JsonFields.optionalString(object, "activity")
                    .orElseThrow(() -> new DefinitionParseException(
                            "\"" + where + "\" is missing \"activity\"")));

            if (!times.add(time)) {
                throw new DefinitionParseException("\"" + field + "\" has two entries at time " + time
                        + ", and nothing decides which of them wins");
            }
            entries.add(new ScheduleDefinition.Entry(time, activity));
        }

        entries.sort(Comparator.comparingInt(ScheduleDefinition.Entry::time));
        return new ScheduleDefinition(ScheduleDefinition.CUSTOM, List.copyOf(entries));
    }

    private static int time(JsonObject object, String where) {
        JsonElement element = object.get("time");
        if (element == null || element.isJsonNull()) {
            throw new DefinitionParseException("\"" + where + "\" is missing \"time\"");
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new DefinitionParseException("\"" + where + ".time\" must be a number");
        }

        int time = element.getAsInt();
        if (time < 0 || time >= DAY_LENGTH) {
            // Rejected rather than wrapped: 25000 is more likely a misunderstanding than a way of writing 1000.
            throw new DefinitionParseException("\"" + where + ".time\" is " + time
                    + ", but a Minecraft day is 0 to " + (DAY_LENGTH - 1)
                    + " ticks, 0 being sunrise and 12000 sunset");
        }
        return time;
    }

    private static ScheduleDefinition shifted(ScheduleDefinition source, String name, int by) {
        List<ScheduleDefinition.Entry> entries = new ArrayList<>();
        for (ScheduleDefinition.Entry entry : source.entries()) {
            entries.add(new ScheduleDefinition.Entry((entry.time() + by) % DAY_LENGTH, entry.activity()));
        }
        entries.sort(Comparator.comparingInt(ScheduleDefinition.Entry::time));
        return new ScheduleDefinition(name, List.copyOf(entries));
    }
}
