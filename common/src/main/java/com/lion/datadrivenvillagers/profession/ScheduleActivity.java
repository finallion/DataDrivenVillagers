package com.lion.datadrivenvillagers.profession;

import com.lion.datadrivenvillagers.DefinitionParseException;

import java.util.Arrays;
import java.util.Locale;

/// Mirrors four of vanilla's `Activity` constants without referencing them, so the parser needs no
/// registry. Only these four: `VillagerEntity.initBrain` gives an adult villager task lists for
/// exactly these, plus `core` (always on) and `panic`, `raid`, `pre_raid`, `hide` (started by
/// sensors). An activity without a task list, like `play`, leaves the brain with nothing to run.
public enum ScheduleActivity {
    IDLE,
    /// Needs the `JOB_SITE` memory.
    WORK,
    /// Needs a meeting point.
    MEET,
    /// Needs a bed claimed as `HOME`. The sleep task never asks the clock, so resting by day works.
    REST;

    public String lower() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ScheduleActivity parse(String raw) {
        for (ScheduleActivity activity : values()) {
            if (activity.lower().equals(raw.toLowerCase(Locale.ROOT))) {
                return activity;
            }
        }
        throw new DefinitionParseException("unknown activity \"" + raw + "\", expected one of "
                + Arrays.stream(values()).map(ScheduleActivity::lower).toList()
                + ". A villager runs no other activity on a clock: core is always on, and panic, "
                + "raid, pre_raid and hide are started by what it senses, not by the time of day");
    }
}
