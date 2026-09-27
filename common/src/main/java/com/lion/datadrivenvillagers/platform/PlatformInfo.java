package com.lion.datadrivenvillagers.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;

import net.minecraft.util.Identifier;

/// Loader name and version, mod version, mod presence; for the doctor report and the trades button.
public class PlatformInfo {

    @ExpectPlatform
    public static String loader() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static String modVersion() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isLoaded(String modId) {
        throw new AssertionError();
    }

    /// The translation key of the name this mod gives a profession at registration.
    public static String villagerNameKey(Identifier profession) {
        return "entity.minecraft.villager." + profession.getPath();
    }
}
