package com.lion.datadrivenvillagers.platform.forge;

import com.lion.datadrivenvillagers.DataDrivenVillagers;

import net.minecraftforge.fml.ModList;

public class PlatformInfoImpl {

    public static String loader() {
        return "Forge " + ModList.get().getModContainerById("forge")
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
