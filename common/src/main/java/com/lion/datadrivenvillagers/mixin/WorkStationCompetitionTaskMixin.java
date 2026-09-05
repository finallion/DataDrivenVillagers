package com.lion.datadrivenvillagers.mixin;

import com.lion.datadrivenvillagers.profession.JobSiteTickets;

import net.minecraft.entity.ai.brain.task.WorkStationCompetitionTask;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.poi.PointOfInterestType;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/// Lets a station with more than one ticket hold more than one villager. `isUsingWorkStationAt` is
/// the filter that decides whether a visible villager is a rival for this block; answering false for
/// a shared station of ours means the competition finds nobody and strips nobody of the job. See
/// `JobSiteTickets`. Other job sites compete as before.
@Mixin(WorkStationCompetitionTask.class)
public abstract class WorkStationCompetitionTaskMixin {

    @Inject(method = "isUsingWorkStationAt", at = @At("HEAD"), cancellable = true)
    private static void datadrivenvillagers$shareTheStation(
            GlobalPos site,
            RegistryEntry<PointOfInterestType> poi,
            VillagerEntity other,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (JobSiteTickets.isShared(poi)) {
            cir.setReturnValue(false);
        }
    }
}
