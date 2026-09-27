package com.lion.datadrivenvillagers.platform;

import net.minecraft.util.Identifier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlatformInfoTest {

    @Test
    void keyHasNoNamespace() {
        assertEquals("entity.minecraft.villager.baker",
                PlatformInfo.villagerNameKey(Identifier.of("datadrivenvillagers", "baker")));
        assertEquals("entity.minecraft.villager.farmer",
                PlatformInfo.villagerNameKey(Identifier.ofVanilla("farmer")));
    }
}
