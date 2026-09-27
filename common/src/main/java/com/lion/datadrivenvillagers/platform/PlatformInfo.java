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

    private static final boolean NEOFORGE = classExists("net.neoforged.fml.loading.FMLEnvironment");

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, PlatformInfo.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /// The key vanilla's `getDefaultName` looks up for this profession, without a `display_name`.
    public static String villagerNameKey(Identifier profession) {
        if (NEOFORGE && !profession.getNamespace().equals("minecraft")) {
            return "entity.minecraft.villager." + profession.getNamespace() + "." + profession.getPath();
        }
        return "entity.minecraft.villager." + profession.getPath();
    }
}
