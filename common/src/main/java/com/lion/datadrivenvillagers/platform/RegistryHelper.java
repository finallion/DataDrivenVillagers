package com.lion.datadrivenvillagers.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;

import net.minecraft.util.Identifier;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.VillagerType;
import net.minecraft.world.poi.PointOfInterestType;

public class RegistryHelper {

    @ExpectPlatform
    public static PointOfInterestType registerPointOfInterestType(Identifier id, PointOfInterestType type) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static VillagerProfession registerVillagerProfession(Identifier id, VillagerProfession profession) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static VillagerType registerVillagerType(Identifier id, VillagerType type) {
        throw new AssertionError();
    }
}
