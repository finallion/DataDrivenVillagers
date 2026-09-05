package com.lion.datadrivenvillagers.profession;

import java.util.List;
import java.util.stream.Collectors;

/// One parsed day plan, in vanilla's shape: switch-over points, each activity running until the next
/// entry, the last wrapping around midnight into the first.
///
/// @param name    the preset a file asked for, or `custom`
/// @param entries at least one, sorted by time, no two at the same tick
public record ScheduleDefinition(String name, List<Entry> entries) {

    public static final String CUSTOM = "custom";

    /// @param time     tick of the day, 0 is sunrise and 12000 is sunset
    /// @param activity what the villager does from that tick until the next entry
    public record Entry(int time, ScheduleActivity activity) {
    }

    public boolean isPreset() {
        return !CUSTOM.equals(name);
    }

    /// One line for `/ddv why` and the startup log.
    public String describe() {
        return entries.stream()
                .map(entry -> entry.time() + " " + entry.activity().lower())
                .collect(Collectors.joining(", "));
    }

    /// Night is 13000 to 23000, the stretch vanilla's sleep and mob rules treat as dark.
    public boolean worksAtNight() {
        for (int time = 13000; time < 23000; time += 1000) {
            if (activityAt(time) == ScheduleActivity.WORK) {
                return true;
            }
        }
        return false;
    }

    /// Same rule as vanilla's `Schedule.getActivityForTime`: the last entry at or before the tick,
    /// and before the first entry the last one of the previous day.
    public ScheduleActivity activityAt(int time) {
        ScheduleActivity current = entries.get(entries.size() - 1).activity();
        for (Entry entry : entries) {
            if (entry.time() > time) {
                break;
            }
            current = entry.activity();
        }
        return current;
    }
}
