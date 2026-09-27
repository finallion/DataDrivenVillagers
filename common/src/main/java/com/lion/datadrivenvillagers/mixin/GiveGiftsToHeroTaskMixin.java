package com.lion.datadrivenvillagers.mixin;

import com.lion.datadrivenvillagers.hook.OptionalHooks;
import com.lion.datadrivenvillagers.profession.ProfessionBehaviours;
import com.lion.datadrivenvillagers.profession.ProfessionDefinition;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.entity.ai.brain.task.GiveGiftsToHeroTask;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.util.Identifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

/// Answers the profession's `gift`. Vanilla keeps a private static map of profession to loot table
/// and asks it twice: `containsKey` checks whether a gift exists, `get` names the loot table.
/// Vanilla excludes a baby villager before either call runs.
@Mixin(GiveGiftsToHeroTask.class)
public abstract class GiveGiftsToHeroTaskMixin {

    @ModifyExpressionValue(method = "getGifts", require = 0,
            at = @At(value = "INVOKE", target = "Ljava/util/Map;containsKey(Ljava/lang/Object;)Z"))
    private boolean datadrivenvillagers$hasGift(boolean original, VillagerEntity villager) {
        OptionalHooks.confirmGift();
        return original || datadrivenvillagers$giftId(villager).isPresent();
    }

    @ModifyExpressionValue(method = "getGifts", require = 0,
            at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object datadrivenvillagers$gift(Object original, VillagerEntity villager) {
        Optional<Identifier> gift = datadrivenvillagers$giftId(villager);
        return gift.isPresent() ? gift.get() : original;
    }

    @Unique
    private static Optional<Identifier> datadrivenvillagers$giftId(VillagerEntity villager) {
        return ProfessionBehaviours.of(villager).flatMap(ProfessionDefinition::gift);
    }
}
