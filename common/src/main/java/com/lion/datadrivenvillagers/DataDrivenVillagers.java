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

    /// Registries freeze before any datapack is read, so this must happen during mod init.
    public static void init() {
        ProfessionLoader.loadAll();
        TypeLoader.registerTypes();
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    /// Bumped per reload; render-thread caches compare it for inequality, so the server thread needs no lock.
    public static int generation() {
        return generation;
    }

    public static void newGeneration() {
        generation++;
    }
}
