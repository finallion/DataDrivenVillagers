package com.lion.datadrivenvillagers.platform.neoforge;

import com.lion.datadrivenvillagers.DataDrivenVillagers;

import net.neoforged.fml.ModList;

public class PlatformInfoImpl {

    public static String loader() {
        return "NeoForge " + ModList.get().getModContainerById("neoforge")
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("?");
    }

    public static String modVersion() {
        return ModList.get().getModContainerById(DataDrivenVillagers.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("?");
    }

    public static boolean isLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }
}
