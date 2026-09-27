package com.lion.datadrivenvillagers.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.poi.PointOfInterestType;
import net.minecraft.world.poi.PointOfInterestTypes;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;
import java.util.Set;

/// Lets the loader read and fill the block-state-to-type map vanilla keeps for its own types only.
@Mixin(PointOfInterestTypes.class)
public interface PointOfInterestTypesAccessor {

    @Accessor("POI_STATES_TO_TYPE")
    static Map<BlockState, RegistryEntry<PointOfInterestType>> ddv$poiStatesToType() {
        throw new AssertionError();
    }

    @Invoker("getStatesOfBlock")
    static Set<BlockState> ddv$getStatesOfBlock(Block block) {
        throw new AssertionError();
    }
}
