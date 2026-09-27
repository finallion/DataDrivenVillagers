package com.lion.datadrivenvillagers.platform.fabric;

import com.lion.datadrivenvillagers.mixin.PointOfInterestTypesAccessor;

import net.minecraft.block.BlockState;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.poi.PointOfInterestType;

import java.util.LinkedHashMap;
import java.util.Map;

public class JobSiteStatesImpl {

    public static RegistryEntry<PointOfInterestType> get(BlockState state) {
        return PointOfInterestTypesAccessor.ddv$poiStatesToType().get(state);
    }

    public static void put(BlockState state, RegistryEntry<PointOfInterestType> jobSite) {
        PointOfInterestTypesAccessor.ddv$poiStatesToType().put(state, jobSite);
    }

    public static void remove(BlockState state) {
        PointOfInterestTypesAccessor.ddv$poiStatesToType().remove(state);
    }

    public static Map<BlockState, RegistryEntry<PointOfInterestType>> all() {
        return new LinkedHashMap<>(PointOfInterestTypesAccessor.ddv$poiStatesToType());
    }
}
