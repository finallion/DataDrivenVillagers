package com.lion.datadrivenvillagers.platform.forge;

import com.lion.datadrivenvillagers.forge.mixin.PointOfInterestTypesAccessor;

import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.poi.PointOfInterestType;

import java.util.LinkedHashMap;
import java.util.Map;

/// Forge patches the map to hold the point of interest instead of its registry entry, so the
/// conversion goes both ways.
public class JobSiteStatesImpl {

    public static RegistryEntry<PointOfInterestType> get(BlockState state) {
        return entry(PointOfInterestTypesAccessor.ddv$forgePoiStatesToType().get(state));
    }

    public static void put(BlockState state, RegistryEntry<PointOfInterestType> jobSite) {
        PointOfInterestTypesAccessor.ddv$forgePoiStatesToType().put(state, jobSite.value());
    }

    public static void remove(BlockState state) {
        PointOfInterestTypesAccessor.ddv$forgePoiStatesToType().remove(state);
    }

    public static Map<BlockState, RegistryEntry<PointOfInterestType>> all() {
        Map<BlockState, RegistryEntry<PointOfInterestType>> states = new LinkedHashMap<>();
        for (Map.Entry<BlockState, PointOfInterestType> claimed
                : PointOfInterestTypesAccessor.ddv$forgePoiStatesToType().entrySet()) {
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
