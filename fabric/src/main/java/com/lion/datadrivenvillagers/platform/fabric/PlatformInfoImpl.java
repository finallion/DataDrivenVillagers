package com.lion.datadrivenvillagers.platform.fabric;

import com.lion.datadrivenvillagers.DataDrivenVillagers;

import net.fabricmc.loader.api.FabricLoader;

public class PlatformInfoImpl {

    public static String loader() {
        return "Fabric " + FabricLoader.getInstance().getModContainer("fabricloader")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("?");
    }

    public static String modVersion() {
        return FabricLoader.getInstance().getModContainer(DataDrivenVillagers.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("?");
    }

    public static boolean isLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }
}
