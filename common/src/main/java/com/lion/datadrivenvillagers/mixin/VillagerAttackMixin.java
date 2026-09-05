package com.lion.datadrivenvillagers.mixin;

import com.lion.datadrivenvillagers.profession.Attack;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import com.google.common.collect.ImmutableList;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.VillagerEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/// Adds the `ATTACK_TARGET` and `ATTACK_COOLING_DOWN` memories and an attack damage attribute to every
/// villager, because a profession changes at runtime while brain profile and attributes are fixed at
/// entity creation. The damage value is set per villager in {@link VillagerEntityMixin}.
/// `createVillagerAttributes` is vanilla code, so this covers NeoForge's attribute registry as well.
@Mixin(VillagerEntity.class)
public abstract class VillagerAttackMixin {

    @ModifyExpressionValue(method = "createBrainProfile",
            at = @At(value = "FIELD", target = "Lnet/minecraft/entity/passive/VillagerEntity;MEMORY_MODULES:Lcom/google/common/collect/ImmutableList;"))
    private static ImmutableList<MemoryModuleType<?>> datadrivenvillagers$attackMemories(
            ImmutableList<MemoryModuleType<?>> original) {
        return ImmutableList.<MemoryModuleType<?>>builder()
                .addAll(original)
                .add(MemoryModuleType.ATTACK_TARGET)
                .add(MemoryModuleType.ATTACK_COOLING_DOWN)
                .build();
    }

    @ModifyReturnValue(method = "createVillagerAttributes", at = @At("RETURN"))
    private static DefaultAttributeContainer.Builder datadrivenvillagers$attackDamage(
            DefaultAttributeContainer.Builder original) {
        return original.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, Attack.DEFAULT_DAMAGE);
    }
}
