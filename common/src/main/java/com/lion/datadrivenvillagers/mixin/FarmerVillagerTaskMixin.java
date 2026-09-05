package com.lion.datadrivenvillagers.mixin;

import com.lion.datadrivenvillagers.profession.ProfessionBehaviours;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.entity.ai.brain.task.FarmerVillagerTask;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/// Second half of `work_behaviour: farm`: `FarmerVillagerTask.shouldRun` checks `matchesKey(FARMER)`
/// again, so handing the task to a profession is not enough on its own.
@Mixin(FarmerVillagerTask.class)
public abstract class FarmerVillagerTaskMixin {

    @ModifyExpressionValue(method = "shouldRun(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/passive/VillagerEntity;)Z",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/registry/entry/RegistryEntry;matchesKey(Lnet/minecraft/registry/RegistryKey;)Z"))
    private boolean datadrivenvillagers$countsAsFarmer(boolean original, ServerWorld world, VillagerEntity villager) {
        return original || ProfessionBehaviours.countsAsFarmer(villager.getVillagerData().profession());
    }
}
