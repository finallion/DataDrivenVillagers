package com.lion.datadrivenvillagers.profession;

import com.lion.datadrivenvillagers.DataDrivenVillagers;

import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.util.Identifier;

import java.util.Optional;

/// Applies a profession's health and attack damage as transient attribute modifiers, never as the base value.
public final class ProfessionAttributes {

    public static final Identifier HEALTH_MODIFIER_ID = DataDrivenVillagers.id("profession_health");
    public static final Identifier ATTACK_MODIFIER_ID = DataDrivenVillagers.id("profession_attack");

    private ProfessionAttributes() {
    }

    /// Sets or drops both modifiers for the villager's current profession; keeps the health ratio if max health moves.
    public static void apply(VillagerEntity villager) {
        Optional<ProfessionDefinition> definition = ProfessionBehaviours.of(villager);
        applyAttack(villager, definition.flatMap(ProfessionDefinition::attack).map(Attack::damage).orElse(null));
        applyHealth(villager, definition.flatMap(ProfessionDefinition::health).orElse(null));
    }

    private static void applyAttack(VillagerEntity villager, Double damage) {
        EntityAttributeInstance attribute = villager.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (attribute == null) {
            return;
        }
        if (damage == null) {
            attribute.removeModifier(ATTACK_MODIFIER_ID);
            return;
        }
        attribute.updateModifier(new EntityAttributeModifier(
                ATTACK_MODIFIER_ID, damage - Attack.DEFAULT_DAMAGE, EntityAttributeModifier.Operation.ADD_VALUE));
    }

    /// A villager leaving a high-health job keeps its health share of the old max, not the raw points.
    private static void applyHealth(VillagerEntity villager, Double health) {
        EntityAttributeInstance attribute = villager.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (attribute == null) {
            return;
        }
        double oldMax = attribute.getValue();
        if (health == null) {
            attribute.removeModifier(HEALTH_MODIFIER_ID);
        } else {
            attribute.updateModifier(new EntityAttributeModifier(HEALTH_MODIFIER_ID,
                    health - ProfessionDefinition.VANILLA_HEALTH, EntityAttributeModifier.Operation.ADD_VALUE));
        }

        double newMax = attribute.getValue();
        if (newMax != oldMax && oldMax > 0 && villager.getHealth() > 0) {
            villager.setHealth((float) (villager.getHealth() * newMax / oldMax));
        }
    }
}
