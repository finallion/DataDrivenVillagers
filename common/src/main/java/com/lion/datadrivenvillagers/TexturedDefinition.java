package com.lion.datadrivenvillagers;

import net.minecraft.util.Identifier;

import java.util.Optional;

/// What the runtime texture loader needs from a profession or villager type. Not in the client
/// package: definitions are parsed on the dedicated server too.
public interface TexturedDefinition {

    String VILLAGER = "villager";
    String ZOMBIE_VILLAGER = "zombie_villager";

    Identifier id();

    /// A resource pack identifier; empty when {@link #textureFile} is used or there is no texture.
    Optional<Identifier> texture();

    /// A png beside the json; empty when {@link #texture} is used or there is no texture.
    Optional<String> textureFile();

    /// The zombie's own image. Empty means the zombie wears the villager's image.
    default Optional<Identifier> zombieTexture() {
        return Optional.empty();
    }

    default Optional<String> zombieTextureFile() {
        return Optional.empty();
    }

    /// The explicit identifier for an entity type, if any.
    default Optional<Identifier> textureFor(String entityType) {
        if (ZOMBIE_VILLAGER.equals(entityType) && zombieTexture().isPresent()) {
            return zombieTexture();
        }
        return texture();
    }

    /// The png beside the json for an entity type: the zombie's own if present, else the villager's.
    default Optional<String> textureFileFor(String entityType) {
        if (ZOMBIE_VILLAGER.equals(entityType) && zombieTextureFile().isPresent()) {
            return zombieTextureFile();
        }
        return textureFile();
    }

    /// The id vanilla derives from the registry id for this entity type.
    Identifier vanillaTextureId(String entityType);
}
