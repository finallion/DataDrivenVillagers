package com.lion.datadrivenvillagers.mixin;

import com.lion.datadrivenvillagers.profession.ProfessionBehaviours;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.entity.ai.brain.task.FarmerVillagerTask;
import net.minecraft.village.VillagerProfession;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/// Second half of `work_behaviour: farm`: `FarmerVillagerTask.shouldRun` compares the profession
/// against `FARMER` again, so handing the task to a profession is not enough on its own. The
/// comparison is on the object, so the answer of `getProfession` is swapped instead of the check.
@Mixin(FarmerVillagerTask.class)
public abstract class FarmerVillagerTaskMixin {

    @ModifyExpressionValue(method = "shouldRun(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/passive/VillagerEntity;)Z",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/village/VillagerData;getProfession()Lnet/minecraft/village/VillagerProfession;"))
    private VillagerProfession datadrivenvillagers$countsAsFarmer(VillagerProfession original) {
        return ProfessionBehaviours.countsAsFarmer(original) ? VillagerProfession.FARMER : original;
    }
}
