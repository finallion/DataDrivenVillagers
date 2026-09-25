package com.lion.datadrivenvillagers.profession;

import com.lion.datadrivenvillagers.TexturedDefinition;

import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Optional;

/// One parsed profession file. Pure data; identifiers are resolved against the registries in
/// {@link ProfessionLoader}. Fields marked "per villager" are looked up at runtime through the
/// profession id, so they follow a reload and apply to overrides; the others are frozen into the
/// `VillagerProfession`/`PointOfInterestType` record at registration.
public record ProfessionDefinition(
        Identifier id,
        Optional<Identifier> overrides,
        List<Identifier> workstations,
        List<Identifier> addWorkstations,
        Optional<String> displayName,
        Optional<Identifier> texture,
        Optional<String> textureFile,
        Optional<Identifier> zombieTexture,
        Optional<String> zombieTextureFile,
        HatKind hat,
        Optional<Identifier> workSound,
        List<Identifier> gatherable,
        List<Identifier> secondarySites,
        Optional<Identifier> gift,
        Optional<ScheduleDefinition> schedule,
        WorkBehaviour workBehaviour,
        Fears fears,
        Optional<Attack> attack,
        Optional<Double> health,
        List<Identifier> villages,
        int ticketCount,
        int searchDistance
) implements TexturedDefinition {

    /// `MobEntity.createMobAttributes` max health, which `createVillagerAttributes` leaves unchanged in 1.20.1.
    public static final double VANILLA_HEALTH = 20.0;

    public String name() {
        return id.getPath();
    }

    /// Runtime lookups (texture, hat, gift) key on this, not on {@link #id}.
    public Identifier target() {
        return overrides.orElse(id);
    }

    public boolean isOverride() {
        return overrides.isPresent();
    }

    /// Built from {@link #id}, not {@link #target()}, so an override's texture stays in our namespace.
    @Override
    public Identifier vanillaTextureId(String entityType) {
        return id.withPath(path -> "textures/entity/" + entityType + "/profession/" + path + ".png");
    }
}
