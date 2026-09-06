package com.lion.datadrivenvillagers.platform.fabric;

import net.minecraft.block.BlockState;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.poi.PointOfInterestType;
import net.minecraft.world.poi.PointOfInterestTypes;

import java.util.LinkedHashMap;
import java.util.Map;

public class JobSiteStatesImpl {

    public static RegistryEntry<PointOfInterestType> get(BlockState state) {
        return PointOfInterestTypes.POI_STATES_TO_TYPE.get(state);
    }

    public static void put(BlockState state, RegistryEntry<PointOfInterestType> jobSite) {
        PointOfInterestTypes.POI_STATES_TO_TYPE.put(state, jobSite);
    }

    public static void remove(BlockState state) {
        PointOfInterestTypes.POI_STATES_TO_TYPE.remove(state);
    }

    public static Map<BlockState, RegistryEntry<PointOfInterestType>> all() {
        return new LinkedHashMap<>(PointOfInterestTypes.POI_STATES_TO_TYPE);
    }
}
