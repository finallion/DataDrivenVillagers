package com.lion.datadrivenvillagers.neoforge.mixin;

import com.lion.datadrivenvillagers.profession.ProfessionBehaviours;
import com.lion.datadrivenvillagers.profession.ProfessionDefinition;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.entity.ai.brain.task.GiveGiftsToHeroTask;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.neoforged.neoforge.registries.datamaps.builtin.RaidHeroGift;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/// NeoForge reads the hero gift from its `raid_hero_gifts` data map instead of vanilla's map, and a
/// datapack cannot name our professions before they exist. The profession's `gift` wins over the map.
@Mixin(GiveGiftsToHeroTask.class)
public abstract class RaidHeroGiftMixin {

    @ModifyExpressionValue(method = "getGifts", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/registry/entry/RegistryEntry;getData(Lnet/neoforged/neoforge/registries/datamaps/DataMapType;)Ljava/lang/Object;"))
    private Object datadrivenvillagers$gift(Object original, VillagerEntity villager) {
        return ProfessionBehaviours.of(villager).flatMap(ProfessionDefinition::gift)
                .<Object>map(id -> new RaidHeroGift(RegistryKey.of(RegistryKeys.LOOT_TABLE, id)))
                .orElse(original);
    }
}
