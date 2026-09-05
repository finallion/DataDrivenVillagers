package com.lion.datadrivenvillagers.mixin;

import com.lion.datadrivenvillagers.profession.JobSiteTickets;

import net.minecraft.entity.ai.brain.task.TakeJobSiteTask;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.poi.PointOfInterestType;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/// Second place where vanilla assumes one worker per block. `TakeJobSiteTask` hands a spotted site to
/// a nearby villager for whom `canUseJobSite` is true, and that is true for one already holding the
/// same block as `JOB_SITE`: the giver forgets `POTENTIAL_JOB_SITE`, nobody releases the ticket.
/// Answering false for a shared station of ours leaves nothing to hand over; other job sites untouched.
@Mixin(TakeJobSiteTask.class)
public abstract class TakeJobSiteTaskMixin {

    @Inject(method = "canUseJobSite", at = @At("HEAD"), cancellable = true)
    private static void datadrivenvillagers$keepTheSpottedSite(
            RegistryEntry<PointOfInterestType> poi,
            VillagerEntity other,
            BlockPos pos,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (JobSiteTickets.isShared(poi)) {
            cir.setReturnValue(false);
        }
    }
}
