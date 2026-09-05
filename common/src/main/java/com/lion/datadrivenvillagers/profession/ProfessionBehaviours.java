package com.lion.datadrivenvillagers.profession;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.poi.PointOfInterestType;

import java.util.Optional;

/// The per-villager questions the behaviour mixins and `/ddv why` ask. Everything here is looked
/// up through the profession id on every call, so it follows a reload and works for overrides.
public final class ProfessionBehaviours {

    private ProfessionBehaviours() {
    }

    public static Optional<ProfessionDefinition> of(VillagerProfession profession) {
        return Optional.ofNullable(Registries.VILLAGER_PROFESSION.getId(profession))
                .flatMap(ProfessionRegistry::get);
    }

    public static Optional<ProfessionDefinition> of(VillagerEntity villager) {
        return of(villager.getVillagerData().getProfession());
    }

    /// Whether vanilla's two `FARMER` checks should also say yes for this profession.
    public static boolean countsAsFarmer(VillagerProfession profession) {
        return of(profession).map(definition -> definition.workBehaviour() == WorkBehaviour.FARM).orElse(false);
    }

    /// Run (true), do not run (false), or no opinion (empty, the sensor then asks vanilla's list).
    /// `flees_from` only ever says yes; `flees_only_from` has the last word, so an unnamed entity is
    /// a firm no.
    public static Optional<Boolean> fears(LivingEntity villager, LivingEntity other) {
        if (!(villager instanceof VillagerEntity entity)) {
            return Optional.empty();
        }
        Optional<ProfessionDefinition> definition = of(entity);
        if (definition.isEmpty()) {
            return Optional.empty();
        }
        Identifier type = Registries.ENTITY_TYPE.getId(other.getType());
        // An attack target is never feared, or the panic task and the attack task fight over the same tick.
        if (definition.get().attack().map(attack -> attack.of(type).isPresent()).orElse(false)) {
            return Optional.of(false);
        }
        Fears fears = definition.get().fears();
        if (!fears.isSet()) {
            return Optional.empty();
        }
        Optional<EntityRange> fear = fears.of(type);
        if (fear.isPresent()) {
            double distance = fear.get().distance();
            boolean close = other.squaredDistanceTo(villager) <= distance * distance;
            // Out of range is "not yet"; only a replacing list turns that into a no.
            return close || fears.replacesVanilla() ? Optional.of(close) : Optional.empty();
        }
        return fears.replacesVanilla() ? Optional.of(false) : Optional.empty();
    }

    /// Vanilla's pick: the first registered profession whose `heldWorkstation` accepts the point of interest.
    public static Optional<RegistryEntry.Reference<VillagerProfession>> professionFor(
            RegistryEntry<PointOfInterestType> poi) {
        return Registries.VILLAGER_PROFESSION.streamEntries()
                .filter(entry -> entry.value().heldWorkstation().test(poi))
                .findFirst();
    }

    /// The `villages` check: only a profession whose file names them refuses, and only a villager type not among them.
    ///
    /// @return the reason it may not take the job, empty when it may
    public static Optional<String> refusal(VillagerEntity villager, RegistryEntry<PointOfInterestType> poi) {
        Optional<RegistryEntry.Reference<VillagerProfession>> profession = professionFor(poi);
        if (profession.isEmpty()) {
            return Optional.empty();
        }
        Optional<ProfessionDefinition> definition = of(profession.get().value());
        if (definition.isEmpty() || definition.get().villages().isEmpty()) {
            return Optional.empty();
        }
        Identifier type = Registries.VILLAGER_TYPE.getId(villager.getVillagerData().getType());
        if (type != null && definition.get().villages().contains(type)) {
            return Optional.empty();
        }
        return Optional.of(profession.get().registryKey().getValue() + " is for " + definition.get().villages()
                + " villagers, this one is " + type);
    }


    /// Whether what last hurt this villager is an attack target. The panic task asks before panicking.
    public static boolean hurtByATarget(LivingEntity entity) {
        if (!(entity instanceof VillagerEntity villager)) {
            return false;
        }
        Optional<Attack> attack = of(villager).flatMap(ProfessionDefinition::attack);
        if (attack.isEmpty()) {
            return false;
        }
        return villager.getBrain().getOptionalMemory(MemoryModuleType.HURT_BY_ENTITY)
                .map(by -> attack.get().of(Registries.ENTITY_TYPE.getId(by.getType())).isPresent())
                .orElse(false);
    }
    /// The nearest visible attack target, empty for a profession without `attacks`.
    public static Optional<LivingEntity> target(VillagerEntity villager) {
        Optional<Attack> attack = of(villager).flatMap(ProfessionDefinition::attack);
        if (attack.isEmpty() || villager.isBaby()) {
            return Optional.empty();
        }
        return villager.getBrain().getOptionalMemory(MemoryModuleType.VISIBLE_MOBS)
                .flatMap(visible -> visible.findFirst(other -> wanted(villager, attack.get(), other)));
    }

    private static boolean wanted(VillagerEntity villager, Attack attack, LivingEntity other) {
        if (!other.isAlive()) {
            return false;
        }
        Optional<EntityRange> target = attack.of(Registries.ENTITY_TYPE.getId(other.getType()));
        if (target.isEmpty()) {
            return false;
        }
        double distance = target.get().distance();
        return other.squaredDistanceTo(villager) <= distance * distance;
    }
}
