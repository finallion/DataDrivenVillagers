package com.lion.datadrivenvillagers.platform.fabric;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.VillagerType;
import net.minecraft.world.poi.PointOfInterestType;

public class RegistryHelperImpl {

    public static PointOfInterestType registerPointOfInterestType(Identifier id, PointOfInterestType type) {
        return Registry.register(Registries.POINT_OF_INTEREST_TYPE, id, type);
    }

    public static VillagerProfession registerVillagerProfession(Identifier id, VillagerProfession profession) {
        return Registry.register(Registries.VILLAGER_PROFESSION, id, profession);
    }

    public static VillagerType registerVillagerType(Identifier id, VillagerType type) {
        return Registry.register(Registries.VILLAGER_TYPE, id, type);
    }
}
