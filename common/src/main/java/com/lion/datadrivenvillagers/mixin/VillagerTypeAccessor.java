package com.lion.datadrivenvillagers.mixin;

import net.minecraft.registry.RegistryKey;
import net.minecraft.village.VillagerType;
import net.minecraft.world.biome.Biome;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/// Lets the loader swap `BIOME_TO_TYPE` with one write, so a worldgen thread reading it during a
/// reload sees either the old map or the new one, never one half-built by many puts and removes.
@Mixin(VillagerType.class)
public interface VillagerTypeAccessor {

    @Accessor("BIOME_TO_TYPE")
    static Map<RegistryKey<Biome>, RegistryKey<VillagerType>> ddv$biomeToType() {
        throw new AssertionError();
    }

    @Accessor("BIOME_TO_TYPE")
    @Mutable
    static void ddv$setBiomeToType(Map<RegistryKey<Biome>, RegistryKey<VillagerType>> biomeToType) {
        throw new AssertionError();
    }
}
