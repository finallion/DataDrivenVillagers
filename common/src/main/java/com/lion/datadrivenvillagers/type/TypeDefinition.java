package com.lion.datadrivenvillagers.type;

import com.lion.datadrivenvillagers.TexturedDefinition;

import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Optional;

/// One parsed villager type file: the texture layer under the profession, chosen by the biome a
/// villager is born in.
///
/// @param id          registry id, derived from the file name
/// @param texture     explicit texture identifier, wins over {@link #textureFile}
/// @param textureFile png next to the json, loaded at runtime
/// @param biomes      biomes claimed outright, they win over anything already mapped
/// @param biomeTags   biome tags, filled in only where nothing is mapped yet
public record TypeDefinition(
        Identifier id,
        Optional<Identifier> texture,
        Optional<String> textureFile,
        List<Identifier> biomes,
        List<Identifier> biomeTags
) implements TexturedDefinition {

    public String name() {
        return id.getPath();
    }

    /// Vanilla builds `textures/entity/<entity>/<layer>/<path>.png` and keeps the namespace.
    @Override
    public Identifier vanillaTextureId(String entityType) {
        return id.withPath(path -> "textures/entity/" + entityType + "/type/" + path + ".png");
    }
}
