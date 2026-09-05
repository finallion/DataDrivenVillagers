package com.lion.datadrivenvillagers.mixin;

import com.lion.datadrivenvillagers.profession.ProfessionDefinition;
import com.lion.datadrivenvillagers.profession.ProfessionRegistry;

import net.minecraft.entity.ai.brain.task.GiveGiftsToHeroTask;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.loot.LootTable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/// Answers `getGiftLootTable` with the profession's `gift`. Vanilla keeps a private static map of
/// profession to loot table and falls back to the unemployed gift for anything not in it.
@Mixin(GiveGiftsToHeroTask.class)
public abstract class GiveGiftsToHeroTaskMixin {

    @Inject(method = "getGiftLootTable(Lnet/minecraft/entity/passive/VillagerEntity;)Lnet/minecraft/registry/RegistryKey;",
            at = @At("HEAD"), cancellable = true)
    private static void datadrivenvillagers$overrideGift(VillagerEntity villager,
                                                         CallbackInfoReturnable<RegistryKey<LootTable>> cir) {
        // Vanilla answers babies before looking at the profession.
        if (villager.isBaby()) {
            return;
        }

        Optional<Identifier> gift = villager.getVillagerData().profession().getKey()
                .map(RegistryKey::getValue)
                .flatMap(ProfessionRegistry::get)
                .flatMap(ProfessionDefinition::gift);

        gift.ifPresent(id -> cir.setReturnValue(RegistryKey.of(RegistryKeys.LOOT_TABLE, id)));
    }
}
