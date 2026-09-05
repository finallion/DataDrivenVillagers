package com.lion.datadrivenvillagers.network;

import com.lion.datadrivenvillagers.DataDrivenVillagers;
import com.lion.datadrivenvillagers.TexturedDefinition;
import com.lion.datadrivenvillagers.profession.HatKind;

import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/// Client-side store of the looks the server sent. Server wins: while {@link #active()} the renderer
/// asks here first and the client's own config folder only fills gaps. Cleared on disconnect.
///
/// Touches no texture manager, so it stays loadable on a dedicated server, where the NeoForge payload
/// registration has to name the handler.
public final class SyncedLooks {

    /// One definition as the server described it; answers the same questions as {@link TexturedDefinition}.
    public record Look(LookPayload.Kind kind, Identifier definition, HatKind hat,
                       Optional<Identifier> texture, Optional<byte[]> png,
                       Optional<Identifier> zombieTexture, Optional<byte[]> zombiePng) {

        public Optional<Identifier> textureFor(String entityType) {
            if (TexturedDefinition.ZOMBIE_VILLAGER.equals(entityType) && zombieTexture.isPresent()) {
                return zombieTexture;
            }
            return texture;
        }

        public Optional<byte[]> pngFor(String entityType) {
            if (TexturedDefinition.ZOMBIE_VILLAGER.equals(entityType) && zombiePng.isPresent()) {
                return zombiePng;
            }
            return png;
        }

        /// Under `synced/`, so a server texture and one from the client's own file never share an id.
        public Identifier textureId(String entityType) {
            return definition.withPath(path -> "textures/entity/" + entityType + "/synced/"
                    + kind.lower() + "/" + path + ".png");
        }
    }

    private static final Map<Identifier, Look> PROFESSIONS = new HashMap<>();
    private static final Map<Identifier, Look> TYPES = new HashMap<>();

    private static boolean active;
    private static int generation;
    private static int expected;

    private SyncedLooks() {
    }

    public static void begin(LooksBeginPayload payload) {
        PROFESSIONS.clear();
        TYPES.clear();
        active = true;
        expected = payload.count();
        // Bumped once per sync, not per look: the renderer's texture cache drops everything when it moves.
        generation++;
        DataDrivenVillagers.LOGGER.info("Server describes {} look(s), the client folder now only fills gaps",
                payload.count());
    }

    public static void accept(LookPayload payload) {
        if (!active) {
            // A look without a begin packet is taken anyway.
            active = true;
            generation++;
        }
        Look look = new Look(payload.kind(), payload.definition(), payload.hat(), payload.texture(),
                payload.png(), payload.zombieTexture(), payload.zombiePng());
        (payload.kind() == LookPayload.Kind.PROFESSION ? PROFESSIONS : TYPES).put(payload.target(), look);
    }

    /// Back to the client's own folder. Called when the client leaves a world.
    public static void clear() {
        if (!active && PROFESSIONS.isEmpty() && TYPES.isEmpty()) {
            return;
        }
        PROFESSIONS.clear();
        TYPES.clear();
        active = false;
        expected = 0;
        generation++;
    }

    /// Whether a server has sent looks since the last connect. While true, an id absent from both
    /// maps has no definition on the server and vanilla is the right answer.
    public static boolean active() {
        return active;
    }

    public static Optional<Look> profession(Identifier target) {
        return Optional.ofNullable(PROFESSIONS.get(target));
    }

    public static Optional<Look> type(Identifier id) {
        return Optional.ofNullable(TYPES.get(id));
    }

    /// Changes on every sync and every clear; only compared.
    public static int generation() {
        return generation;
    }

    public static int received() {
        return PROFESSIONS.size() + TYPES.size();
    }

    public static int expected() {
        return expected;
    }
}
