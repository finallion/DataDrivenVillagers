package com.lion.datadrivenvillagers.forge.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.world.poi.PointOfInterestType;
import net.minecraft.world.poi.PointOfInterestTypes;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/// Forge patches this map's value to the point of interest itself, not a registry entry.
@Mixin(PointOfInterestTypes.class)
public interface PointOfInterestTypesAccessor {

    @Accessor("POI_STATES_TO_TYPE")
    static Map<BlockState, PointOfInterestType> ddv$poiStatesToType() {
        throw new AssertionError();
    }
}
