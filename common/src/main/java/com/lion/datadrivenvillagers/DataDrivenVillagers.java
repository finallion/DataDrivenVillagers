package com.lion.datadrivenvillagers;

import com.lion.datadrivenvillagers.profession.ProfessionLoader;
import com.lion.datadrivenvillagers.type.TypeLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.util.Identifier;

public class DataDrivenVillagers {

    public static final String MOD_ID = "datadrivenvillagers";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static int generation;

    /// Mod init is the only window in which professions, points of interest and villager types can be
    /// registered: their registries freeze before any datapack is read.
    public static void init() {
        ProfessionLoader.loadAll();
        TypeLoader.registerTypes();
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    /// Bumped on every reload. Caches (decoded textures on the render thread) compare it instead of
    /// being invalidated from the server thread, so no lock is needed. Only inequality matters.
    public static int generation() {
        return generation;
    }

    public static void newGeneration() {
        generation++;
    }
}
