package com.lion.datadrivenvillagers.mixin;

import com.lion.datadrivenvillagers.profession.Attack;
import com.lion.datadrivenvillagers.profession.ProfessionBehaviours;
import com.lion.datadrivenvillagers.profession.ProfessionDefinition;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.entity.ai.brain.task.ForgetAttackTargetTask;
import net.minecraft.entity.ai.brain.task.MeleeAttackTask;
import net.minecraft.entity.ai.brain.task.RangedApproachTask;
import net.minecraft.entity.ai.brain.task.Task;
import net.minecraft.entity.ai.brain.task.UpdateAttackTargetTask;
import net.minecraft.entity.ai.brain.task.VillagerTaskListProvider;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.village.VillagerProfession;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

/// `work_behaviour: farm`: `createWorkTasks` asks `matchesKey(FARMER)` twice (task choice and random
/// weights); both answers are widened, so a farming profession runs exactly the farmer's tasks.
/// `attacks`: the four piglin combat tasks are appended to the core list, which runs in every
/// activity, so a guard fights at night too.
@Mixin(VillagerTaskListProvider.class)
public abstract class VillagerTaskListProviderMixin {

    @ModifyExpressionValue(method = "createWorkTasks",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/registry/entry/RegistryEntry;matchesKey(Lnet/minecraft/registry/RegistryKey;)Z"))
    private static boolean datadrivenvillagers$countsAsFarmer(boolean original,
                                                              RegistryEntry<VillagerProfession> profession,
                                                              float speed) {
        return original || ProfessionBehaviours.countsAsFarmer(profession);
    }

    @ModifyReturnValue(method = "createCoreTasks", at = @At("RETURN"))
    private static ImmutableList<Pair<Integer, ? extends Task<? super VillagerEntity>>> datadrivenvillagers$attackTasks(
            ImmutableList<Pair<Integer, ? extends Task<? super VillagerEntity>>> original,
            RegistryEntry<VillagerProfession> profession, float speed) {
        Optional<Attack> attack = ProfessionBehaviours.of(profession).flatMap(ProfessionDefinition::attack);
        return attack.map(value -> ImmutableList.<Pair<Integer, ? extends Task<? super VillagerEntity>>>builder()
                .addAll(original)
                .add(Pair.of(10, UpdateAttackTargetTask.create((world, villager) -> ProfessionBehaviours.target(villager))))
                .add(Pair.of(10, ForgetAttackTargetTask.create()))
                .add(Pair.of(10, RangedApproachTask.create(speed)))
                .add(Pair.of(10, MeleeAttackTask.create(value.cooldown())))
                .build()).orElse(original);
    }
}
