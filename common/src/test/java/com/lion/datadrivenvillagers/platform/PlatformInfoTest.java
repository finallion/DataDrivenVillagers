package com.lion.datadrivenvillagers.platform;

import net.minecraft.util.Identifier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// No NeoForge class is on this module's test classpath, so the probe always takes the Fabric branch here.
class PlatformInfoTest {

    @Test
    void keyDropsTheNamespaceWithoutNeoForgeOnTheClasspath() {
        assertEquals("entity.minecraft.villager.baker",
                PlatformInfo.villagerNameKey(new Identifier("datadrivenvillagers", "baker")));
        assertEquals("entity.minecraft.villager.farmer",
                PlatformInfo.villagerNameKey(new Identifier("minecraft", "farmer")));
    }
}
