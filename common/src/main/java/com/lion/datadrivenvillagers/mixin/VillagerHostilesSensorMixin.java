package com.lion.datadrivenvillagers.mixin;

import com.lion.datadrivenvillagers.profession.ProfessionBehaviours;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.sensor.VillagerHostilesSensor;
import net.minecraft.server.world.ServerWorld;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/// `flees_from` and `flees_only_from`. The sensor's danger map is private and immutable, so `matches`
/// is answered ahead of it: `flees_from` only ever answers yes and falls through otherwise;
/// `flees_only_from` answers yes or no and vanilla is never asked.
@Mixin(VillagerHostilesSensor.class)
public abstract class VillagerHostilesSensorMixin {

    @Inject(
            method = "matches(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/entity/LivingEntity;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void datadrivenvillagers$fearMore(
            ServerWorld world,
            LivingEntity villager,
            LivingEntity other,
            CallbackInfoReturnable<Boolean> cir
    ) {
        ProfessionBehaviours.fears(villager, other).ifPresent(cir::setReturnValue);
    }
}
