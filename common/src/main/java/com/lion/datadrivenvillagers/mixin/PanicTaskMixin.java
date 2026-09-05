package com.lion.datadrivenvillagers.mixin;

import com.lion.datadrivenvillagers.profession.ProfessionBehaviours;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.task.PanicTask;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/// No panic when hurt by an entity the profession attacks. Panic has two triggers, `NEAREST_HOSTILE`
/// (already filtered by the fear list) and `HURT_BY`; `wasHurt` only asks whether something hurt the
/// villager, not what. Hurt by anything else still panics.
@Mixin(PanicTask.class)
public abstract class PanicTaskMixin {

    @Inject(method = "wasHurt(Lnet/minecraft/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private static void datadrivenvillagers$notByATarget(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (ProfessionBehaviours.hurtByATarget(entity)) {
            cir.setReturnValue(false);
        }
    }
}
