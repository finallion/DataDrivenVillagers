package com.lion.datadrivenvillagers.neoforge.mixin;

import com.lion.datadrivenvillagers.type.TypeLoader;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.village.VillagerType;
import net.minecraft.world.biome.Biome;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/// NeoForge asks the `neoforge:villager_types` data map before vanilla's `BIOME_TO_TYPE`. This mixin
/// answers first for a biome DDV names in `biomes`, so that claim wins over the data map. A biome DDV
/// claims only through a tag is not covered here; the data map keeps the last word for it.
@Mixin(VillagerType.class)
public abstract class VillagerTypeByBiomeMixin {

    @Inject(method = "forBiome", at = @At("HEAD"), cancellable = true)
    private static void datadrivenvillagers$ownClaimFirst(RegistryEntry<Biome> biomeEntry,
                                                           CallbackInfoReturnable<RegistryKey<VillagerType>> cir) {
        Optional<RegistryKey<Biome>> key = biomeEntry.getKey();
        if (key.isEmpty()) {
            return;
        }
        Optional<RegistryKey<VillagerType>> claim = TypeLoader.ownClaim(key.get());
        if (claim.isPresent()) {
            cir.setReturnValue(claim.get());
        }
    }
}
