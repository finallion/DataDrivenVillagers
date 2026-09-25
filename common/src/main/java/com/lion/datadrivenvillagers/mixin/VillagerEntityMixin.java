package com.lion.datadrivenvillagers.mixin;

import com.lion.datadrivenvillagers.profession.Attack;
import com.lion.datadrivenvillagers.profession.ProfessionBehaviours;
import com.lion.datadrivenvillagers.profession.ProfessionDefinition;
import com.lion.datadrivenvillagers.profession.VillagerSchedules;

import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.Registries;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/// Applies the profession's day plan, attack damage and max health at the tail of `initBrain`.
/// Vanilla gives every adult `Schedule.VILLAGER_DEFAULT`; `initBrain` ends with `refreshActivities`,
/// so the brain switches to our plan on the next `ScheduleActivityTask` run, at most 20 ticks later.
/// `reinitializeBrain` (job taken or lost) calls `initBrain`, so this also runs on profession changes.
@Mixin(VillagerEntity.class)
public abstract class VillagerEntityMixin {


    @Inject(method = "initBrain(Lnet/minecraft/entity/ai/brain/Brain;)V", at = @At("TAIL"))
    private void datadrivenvillagers$applySchedule(Brain<VillagerEntity> brain, CallbackInfo ci) {
        VillagerEntity villager = (VillagerEntity) (Object) this;

        // A baby keeps VILLAGER_BABY.
        if (villager.isBaby()) {
            return;
        }

        Optional.ofNullable(Registries.VILLAGER_PROFESSION.getId(villager.getVillagerData().getProfession()))
                .flatMap(VillagerSchedules::of)
                .ifPresent(brain::setSchedule);

        // The attribute exists on every villager (VillagerAttackMixin); the value is per profession.
        double damage = ProfessionBehaviours.of(villager).flatMap(ProfessionDefinition::attack)
                .map(Attack::damage).orElse(Attack.DEFAULT_DAMAGE);
        EntityAttributeInstance attribute = villager.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (attribute != null) {
            attribute.setBaseValue(damage);
        }

        // Always set: a villager leaving a high-health job must drop back to vanilla's default 20.
        double maxHealth = ProfessionBehaviours.of(villager).flatMap(ProfessionDefinition::health)
                .orElse(ProfessionDefinition.VANILLA_HEALTH);
        EntityAttributeInstance health = villager.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (health != null && health.getBaseValue() != maxHealth) {
            boolean full = villager.getHealth() >= villager.getMaxHealth();
            health.setBaseValue(maxHealth);
            if (full || villager.getHealth() > villager.getMaxHealth()) {
                villager.setHealth(villager.getMaxHealth());
            }
        }
    }
}
