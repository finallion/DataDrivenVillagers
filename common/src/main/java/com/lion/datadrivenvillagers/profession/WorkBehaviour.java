package com.lion.datadrivenvillagers.profession;

import com.lion.datadrivenvillagers.DefinitionParseException;

import java.util.Arrays;
import java.util.Locale;

/// What a villager does at work. Vanilla gates the farmer's extra routine (harvest, plant, bone
/// meal) on `matchesKey(FARMER)` in two places; `FARM` makes a profession of ours pass those checks.
/// Only two values because the farm and bone meal tasks are not separable in vanilla.
public enum WorkBehaviour {
    /// Vanilla's routine for every non-farmer: go to the station, look busy, restock trades.
    STATION,
    /// The farmer's routine on top. Needs farmland in `secondary_job_sites` (filled in by the parser
    /// when absent) and seeds in `gatherable_items`.
    FARM;

    public String lower() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static WorkBehaviour parse(String raw) {
        for (WorkBehaviour behaviour : values()) {
            if (behaviour.lower().equals(raw.toLowerCase(Locale.ROOT))) {
                return behaviour;
            }
        }
        throw new DefinitionParseException("unknown work_behaviour \"" + raw + "\", expected one of "
                + Arrays.stream(values()).map(WorkBehaviour::lower).toList());
    }
}
