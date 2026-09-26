package com.lion.datadrivenvillagers.mixin;

import com.lion.datadrivenvillagers.profession.ProfessionAttributes;
import com.lion.datadrivenvillagers.profession.VillagerSchedules;

import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.storage.ReadView;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// Applies the profession's day plan, attack damage and max health at the tail of `initBrain`.
/// Vanilla gives every adult `Schedule.VILLAGER_DEFAULT`; `initBrain` ends with `refreshActivities`,
/// so the brain switches to our plan on the next `ScheduleActivityTask` run, at most 20 ticks later.
/// `reinitializeBrain` (job taken or lost, or a fresh load) calls `initBrain`, so this also runs on
/// every profession change.
@Mixin(VillagerEntity.class)
public abstract class VillagerEntityMixin {

    @Inject(method = "initBrain(Lnet/minecraft/entity/ai/brain/Brain;)V", at = @At("TAIL"))
    private void datadrivenvillagers$applySchedule(Brain<VillagerEntity> brain, CallbackInfo ci) {
        VillagerEntity villager = (VillagerEntity) (Object) this;

        // The brain never ticks on the client, and VillagerSchedules keeps a server-only static map.
        if (villager.getWorld().isClient) {
            return;
        }

        // A baby keeps VILLAGER_BABY; its attributes still follow the profession.
        if (!villager.isBaby()) {
            villager.getVillagerData().profession().getKey()
                    .map(RegistryKey::getValue)
                    .flatMap(VillagerSchedules::of)
                    .ifPresent(brain::setSchedule);
        }

        ProfessionAttributes.apply(villager);
    }

    /// LivingEntity clamps `Health` before the profession is known; this sets it again against the final maximum.
    @Inject(method = "readCustomData(Lnet/minecraft/storage/ReadView;)V", at = @At("TAIL"))
    private void datadrivenvillagers$restoreHealth(ReadView view, CallbackInfo ci) {
        VillagerEntity villager = (VillagerEntity) (Object) this;
        ProfessionAttributes.apply(villager);
        villager.setHealth(view.getFloat("Health", villager.getHealth()));
    }
}
