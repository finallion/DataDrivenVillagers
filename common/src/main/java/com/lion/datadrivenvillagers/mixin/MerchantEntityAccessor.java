package com.lion.datadrivenvillagers.mixin;

import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.village.TradeOfferList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/// Reads the offer list without creating it. `getOffers()` builds and caches the list on first call,
/// so calling it from a report would freeze an empty list on a villager whose trades are not in yet.
@Mixin(MerchantEntity.class)
public interface MerchantEntityAccessor {

    @Accessor("offers")
    TradeOfferList ddv$offersOrNull();
}
