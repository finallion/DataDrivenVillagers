package com.lion.datadrivenvillagers.profession;

import com.lion.datadrivenvillagers.DataDrivenVillagers;

import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.Schedule;
import net.minecraft.entity.ai.brain.ScheduleBuilder;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/// Builds the `Schedule` a villager brain holds from a parsed day plan. No registry entry needed:
/// a brain holds the object itself and its codec serialises memories only, so a plan can be swapped
/// at any time. Cached per profession (`initBrain` runs on every villager load), invalidated by the
/// generation counter.
public final class VillagerSchedules {

    private static final Map<Identifier, Schedule> BUILT = new HashMap<>();

    private static int generation = -1;

    private VillagerSchedules() {
    }

    /// `profession` is the vanilla id for an override; returns the plan it asks for, absent when vanilla decides.
    public static Optional<Schedule> of(Identifier profession) {
        if (generation != DataDrivenVillagers.generation()) {
            generation = DataDrivenVillagers.generation();
            BUILT.clear();
        }

        Optional<ScheduleDefinition> wanted = ProfessionRegistry.get(profession)
                .flatMap(ProfessionDefinition::schedule);
        if (wanted.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(BUILT.computeIfAbsent(profession, id -> build(wanted.get())));
    }

    /// Package private for the test, which reads the result back through vanilla's `getActivityForTime`.
    static Schedule build(ScheduleDefinition definition) {
        ScheduleBuilder builder = new ScheduleBuilder(new Schedule());
        for (ScheduleDefinition.Entry entry : definition.entries()) {
            builder.withActivity(entry.time(), activity(entry.activity()));
        }
        return builder.build();
    }

    private static Activity activity(ScheduleActivity activity) {
        return switch (activity) {
            case IDLE -> Activity.IDLE;
            case WORK -> Activity.WORK;
            case MEET -> Activity.MEET;
            case REST -> Activity.REST;
        };
    }
}
