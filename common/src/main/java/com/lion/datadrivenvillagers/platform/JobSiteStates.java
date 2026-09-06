package com.lion.datadrivenvillagers.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;

import net.minecraft.block.BlockState;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.poi.PointOfInterestType;

import java.util.Map;

/// `PointOfInterestTypes.POI_STATES_TO_TYPE`, the map the job site sensor reads. Fabric holds a
/// registry entry in it, Forge the point of interest itself; every read and write goes through here.
public class JobSiteStates {

    /// @return null when no job site claims the state
    @ExpectPlatform
    public static RegistryEntry<PointOfInterestType> get(BlockState state) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void put(BlockState state, RegistryEntry<PointOfInterestType> jobSite) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void remove(BlockState state) {
        throw new AssertionError();
    }

    /// A copy, for reports that walk every claimed state.
    @ExpectPlatform
    public static Map<BlockState, RegistryEntry<PointOfInterestType>> all() {
        throw new AssertionError();
    }
}
