package com.lion.datadrivenvillagers.profession;

import com.lion.datadrivenvillagers.TexturedDefinition;

import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Optional;

/// One parsed profession file. Pure data; identifiers are resolved against the registries in
/// {@link ProfessionLoader}. Fields marked "per villager" are looked up at runtime through the
/// profession id, so they follow a reload and apply to overrides; the others are frozen into the
/// `VillagerProfession`/`PointOfInterestType` record at registration.
///
/// @param id             derived from the file name, always in our namespace; also the texture path, see {@link #vanillaTextureId}
/// @param overrides      an existing profession this file modifies instead of creating one, see {@link #target()}
/// @param workstations   blocks that become the job site, at least one, empty when overriding
/// @param addWorkstations blocks handed to the overridden profession's existing job site
/// @param displayName    used when no language file translates the profession
/// @param texture        explicit texture identifier, wins over {@link #textureFile}
/// @param textureFile    png next to the json, loaded at runtime
/// @param zombieTexture  the zombie villager's image, absent means {@link #texture}
/// @param zombieTextureFile same, as a png next to the json
/// @param hat            whether the villager type's hat underneath is drawn
/// @param workSound      played while the villager works at the station
/// @param gatherable     items the villager picks up
/// @param secondarySites blocks the villager treats as secondary job sites
/// @param gift           loot table thrown at a Hero of the Village, absent means vanilla decides
/// @param schedule       day plan, absent means vanilla's. Per villager
/// @param workBehaviour  see {@link WorkBehaviour}. Per villager
/// @param fears          what it runs from on sight, on top of or instead of vanilla's list. Per villager
/// @param attack         what it goes after and how hard it hits. Per villager, rebuilds the brain
/// @param health         max health, absent means vanilla's 20. Per villager, set with the brain
/// @param villages       villager types allowed to take this job, empty means any. Per villager
/// @param ticketCount    how many villagers may claim one station
/// @param searchDistance how far a villager looks for the station
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

    /// `MobEntity.createMobAttributes` max health, which `createVillagerAttributes` leaves unchanged in 1.21.1.
    public static final double VANILLA_HEALTH = 20.0;

    public String name() {
        return id.getPath();
    }

    /// The profession this file applies to: itself, or the overridden one. Runtime lookups (texture,
    /// hat, gift) key on this.
    public Identifier target() {
        return overrides.orElse(id);
    }

    public boolean isOverride() {
        return overrides.isPresent();
    }

    /// Built from {@link #id}, not {@link #target()}: an override's texture stays in our namespace
    /// instead of shadowing the vanilla one a resource pack may write to.
    @Override
    public Identifier vanillaTextureId(String entityType) {
        return id.withPath(path -> "textures/entity/" + entityType + "/profession/" + path + ".png");
    }
}
