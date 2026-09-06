package com.lion.datadrivenvillagers.platform.forge;

import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.poi.PointOfInterestType;
import net.minecraft.world.poi.PointOfInterestTypes;

import java.util.LinkedHashMap;
import java.util.Map;

/// Forge patches the map to hold the point of interest instead of its registry entry, so the
/// conversion goes both ways.
public class JobSiteStatesImpl {

    public static RegistryEntry<PointOfInterestType> get(BlockState state) {
        return entry(PointOfInterestTypes.POI_STATES_TO_TYPE.get(state));
    }

    public static void put(BlockState state, RegistryEntry<PointOfInterestType> jobSite) {
        PointOfInterestTypes.POI_STATES_TO_TYPE.put(state, jobSite.value());
    }

    public static void remove(BlockState state) {
        PointOfInterestTypes.POI_STATES_TO_TYPE.remove(state);
    }

    public static Map<BlockState, RegistryEntry<PointOfInterestType>> all() {
        Map<BlockState, RegistryEntry<PointOfInterestType>> states = new LinkedHashMap<>();
        for (Map.Entry<BlockState, PointOfInterestType> claimed : PointOfInterestTypes.POI_STATES_TO_TYPE.entrySet()) {
            RegistryEntry<PointOfInterestType> entry = entry(claimed.getValue());
            if (entry != null) {
                states.put(claimed.getKey(), entry);
            }
        }
        return states;
    }

    private static RegistryEntry<PointOfInterestType> entry(PointOfInterestType type) {
        return type == null ? null : Registries.POINT_OF_INTEREST_TYPE.getEntry(type);
    }
}
