package com.lion.datadrivenvillagers.command;

import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.poi.PointOfInterest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// Who holds the places on a job site block: villagers working there (JOB_SITE), on the way
/// (POTENTIAL_JOB_SITE, the ticket is taken when the villager sets off), or nobody (zombified or
/// removed without dying). Read-only.
final class Holders {

    /// Wider than the 48-block station list: a worker missed here would count as "nobody".
    private static final double REACH = 96;

    private final Map<BlockPos, Integer> working = new HashMap<>();
    private final Map<BlockPos, Integer> coming = new HashMap<>();
    private int heldByNobody;

    private Holders() {
    }

    static Holders around(ServerWorld world, BlockPos centre) {
        Holders holders = new Holders();
        Box box = Box.of(Vec3d.ofCenter(centre), REACH * 2, REACH * 2, REACH * 2);
        for (VillagerEntity villager : world.getEntitiesByClass(VillagerEntity.class, box, any -> true)) {
            Brain<VillagerEntity> brain = villager.getBrain();
            count(brain, MemoryModuleType.JOB_SITE, world, holders.working);
            count(brain, MemoryModuleType.POTENTIAL_JOB_SITE, world, holders.coming);
        }
        return holders;
    }

    private static void count(Brain<VillagerEntity> brain, MemoryModuleType<GlobalPos> memory,
                              ServerWorld world, Map<BlockPos, Integer> into) {
        brain.getOptionalMemory(memory)
                .filter(pos -> pos.dimension().equals(world.getRegistryKey()))
                .ifPresent(pos -> into.merge(pos.pos(), 1, Integer::sum));
    }

    /// "1/3 free, 1 working there, 1 held by nobody". Also counts towards {@link #nobodyNote}.
    String describe(PointOfInterest station) {
        int free = station.getFreeTickets();
        int total = station.getType().value().ticketCount();
        int taken = total - free;
        if (taken <= 0) {
            return free + "/" + total + " free";
        }
        int workers = working.getOrDefault(station.getPos(), 0);
        int walking = coming.getOrDefault(station.getPos(), 0);
        int nobody = taken - workers - walking;
        List<String> who = new ArrayList<>();
        if (workers > 0) {
            who.add(workers + " working there");
        }
        if (walking > 0) {
            who.add(walking + " on the way");
        }
        if (nobody > 0) {
            who.add(nobody + " held by nobody");
            heldByNobody += nobody;
        }
        return free + "/" + total + " free, " + String.join(", ", who);
    }

    /// Summary suffix for places held by nobody; call after every {@link #describe}.
    String nobodyNote() {
        if (heldByNobody == 0) {
            return "";
        }
        return "; " + heldByNobody + " place(s) held by nobody: a villager that was zombified or removed "
                + "without dying keeps its place, break and replace the block";
    }
}
