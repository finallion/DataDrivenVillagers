package com.lion.datadrivenvillagers.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;

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
}
