package com.lion.datadrivenvillagers.profession;

import com.lion.datadrivenvillagers.DataDrivenVillagers;

import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.VillagerEntity;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/// Applies a profession's health and attack damage as transient attribute modifiers, never as the base value.
public final class ProfessionAttributes {

    public static final UUID HEALTH_MODIFIER_ID = UUID.nameUUIDFromBytes(
            (DataDrivenVillagers.MOD_ID + ":profession_health").getBytes(StandardCharsets.UTF_8));
    public static final UUID ATTACK_MODIFIER_ID = UUID.nameUUIDFromBytes(
            (DataDrivenVillagers.MOD_ID + ":profession_attack").getBytes(StandardCharsets.UTF_8));

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
        attribute.removeModifier(ATTACK_MODIFIER_ID);
        if (damage == null) {
            return;
        }
        attribute.addTemporaryModifier(new EntityAttributeModifier(ATTACK_MODIFIER_ID, "Profession attack damage",
                damage - Attack.DEFAULT_DAMAGE, EntityAttributeModifier.Operation.ADDITION));
    }

    /// A villager leaving a high-health job keeps its health share of the old max, not the raw points.
    private static void applyHealth(VillagerEntity villager, Double health) {
        EntityAttributeInstance attribute = villager.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (attribute == null) {
            return;
        }
        double oldMax = attribute.getValue();
        attribute.removeModifier(HEALTH_MODIFIER_ID);
        if (health != null) {
            attribute.addTemporaryModifier(new EntityAttributeModifier(HEALTH_MODIFIER_ID, "Profession max health",
                    health - ProfessionDefinition.VANILLA_HEALTH, EntityAttributeModifier.Operation.ADDITION));
        }

        double newMax = attribute.getValue();
        if (newMax != oldMax && oldMax > 0 && villager.getHealth() > 0) {
            villager.setHealth((float) (villager.getHealth() * newMax / oldMax));
        }
    }
}
