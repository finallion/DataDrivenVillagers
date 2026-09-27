package com.lion.datadrivenvillagers.network;

import com.lion.datadrivenvillagers.ConfigFiles;
import com.lion.datadrivenvillagers.DataDrivenVillagers;
import com.lion.datadrivenvillagers.PngHeader;
import com.lion.datadrivenvillagers.TexturedDefinition;
import com.lion.datadrivenvillagers.platform.Network;
import com.lion.datadrivenvillagers.profession.HatKind;
import com.lion.datadrivenvillagers.profession.ProfessionDefinition;
import com.lion.datadrivenvillagers.profession.ProfessionLoader;
import com.lion.datadrivenvillagers.profession.ProfessionRegistry;
import com.lion.datadrivenvillagers.type.TypeDefinition;
import com.lion.datadrivenvillagers.type.TypeLoader;
import com.lion.datadrivenvillagers.type.TypeRegistry;

import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// Server side of look sync: builds the packets from the registries and the pngs on disk. A reload
/// bumps the mod's generation counter and empties the whole cache, so a png is re-read only then.
public final class LookSync {

    /// 3-byte VarInt frame limit; vanilla's 1 MB cap covers only payloads the receiver does not know.
    static final int MAX_PACKET_BYTES = 2_097_151;

    /// One `LookPayload` carries two images, so this caps each one small enough that both still fit.
    public static final int MAX_PNG_BYTES = 900_000;

    /// Ids, hat, the optionals and the frame, generously.
    static final int PACKET_HEADROOM = 16_384;

    /// One cached look plus what it was built from, so a later call can tell whether it is still fresh.
    private record Cached(TexturedDefinition definition, HatKind hat, LookPayload payload) {
    }

    private static final Map<Identifier, Cached> CACHE = new HashMap<>();

    /// The generation this cache was built under; a reload bumps the mod's counter and empties it.
    private static int cachedGeneration = -1;

    private LookSync() {
    }

    public static List<CustomPayload> payloads() {
        int generation = DataDrivenVillagers.generation();
        if (generation != cachedGeneration) {
            CACHE.clear();
            cachedGeneration = generation;
        }

        List<LookPayload> looks = new ArrayList<>();
        Set<Identifier> present = new HashSet<>();
        Path professions = ProfessionLoader.directory();
        for (ProfessionDefinition definition : ProfessionRegistry.ordered()) {
            present.add(definition.target());
            looks.add(cached(LookPayload.Kind.PROFESSION, definition.target(), definition, definition.hat(), professions));
        }
        Path types = TypeLoader.directory();
        for (TypeDefinition definition : TypeRegistry.ordered()) {
            present.add(definition.id());
            looks.add(cached(LookPayload.Kind.TYPE, definition.id(), definition, HatKind.NONE, types));
        }
        // A profession or type that is gone should not keep its stale look alive forever.
        CACHE.keySet().retainAll(present);

        List<CustomPayload> payloads = new ArrayList<>();
        payloads.add(new LooksBeginPayload(looks.size()));
        payloads.addAll(looks);
        return payloads;
    }

    /// Reused as long as neither the definition nor the hat changed since this generation's cache was built.
    private static LookPayload cached(LookPayload.Kind kind, Identifier target, TexturedDefinition definition,
                                      HatKind hat, Path folder) {
        Cached previous = CACHE.get(target);
        if (previous != null && previous.definition().equals(definition) && previous.hat() == hat) {
            return previous.payload();
        }

        LookPayload built = look(kind, target, definition, hat, folder);
        CACHE.put(target, new Cached(definition, hat, built));
        return built;
    }

    private static LookPayload look(LookPayload.Kind kind, Identifier target, TexturedDefinition definition,
                                    HatKind hat, Path folder) {
        Optional<byte[]> villager = bytes(folder, definition.textureFile(), definition.id());
        Optional<byte[]> zombie = bytes(folder, definition.zombieTextureFile(), definition.id());

        // Guards the day MAX_PNG_BYTES and MAX_PACKET_BYTES change independently of each other.
        if (villager.isPresent() && zombie.isPresent()
                && villager.get().length + zombie.get().length + PACKET_HEADROOM > MAX_PACKET_BYTES) {
            DataDrivenVillagers.LOGGER.warn("{}: villager and zombie image together do not fit one packet; "
                    + "the zombie image is not sent, players use their own copy if they have one", definition.id());
            zombie = Optional.empty();
        }

        return new LookPayload(kind, target, definition.id(), hat,
                definition.texture(), villager,
                definition.zombieTexture(), zombie);
    }

    private static Optional<byte[]> bytes(Path folder, Optional<String> file, Identifier id) {
        if (file.isEmpty()) {
            return Optional.empty();
        }
        // The parser holds the name to a plain file name; this is the second lock on the same door.
        Optional<Path> inside = ConfigFiles.resolveInside(folder, file.get());
        if (inside.isEmpty()) {
            DataDrivenVillagers.LOGGER.warn("{} names \"{}\", which is not a file inside {}; not sent",
                    id, file.get(), folder);
            return Optional.empty();
        }
        Path png = inside.get();
        if (!Files.isRegularFile(png)) {
            // Already reported by /ddv why; no log line per join.
            return Optional.empty();
        }
        try {
            // Same verdict the client reaches before decoding, so it never receives a file it would refuse anyway.
            Optional<String> rejected = PngHeader.rejection(png);
            if (rejected.isPresent()) {
                DataDrivenVillagers.LOGGER.warn("{} is {}; not sent to players, and a client refuses its "
                        + "own copy of {} for the same reason", png, rejected.get(), id);
                return Optional.empty();
            }
            Optional<byte[]> read = readUpTo(png, MAX_PNG_BYTES);
            if (read.isEmpty()) {
                DataDrivenVillagers.LOGGER.warn("{} is over {} bytes, too large to send to players; they "
                        + "will use their own copy of {} if they have one", png, MAX_PNG_BYTES, id);
            }
            return read;
        } catch (IOException e) {
            DataDrivenVillagers.LOGGER.error("Could not read {} to send it to players", png, e);
            return Optional.empty();
        }
    }

    /// Empty once the file holds more than `limit` bytes.
    private static Optional<byte[]> readUpTo(Path file, int limit) throws IOException {
        byte[] buffer = new byte[limit + 1];
        int total = 0;
        try (InputStream in = Files.newInputStream(file)) {
            int read;
            while (total < buffer.length && (read = in.read(buffer, total, buffer.length - total)) != -1) {
                total += read;
            }
        }
        return total > limit ? Optional.empty() : Optional.of(Arrays.copyOf(buffer, total));
    }

    /// @return how many looks were sent, the begin packet not counted
    public static int send(ServerPlayerEntity player) {
        return send(player, payloads());
    }

    private static int send(ServerPlayerEntity player, List<CustomPayload> payloads) {
        for (CustomPayload payload : payloads) {
            Network.send(player, payload);
        }
        return payloads.size() - 1;
    }

    /// In single player, this is how the integrated server's new png reaches the client's own renderer.
    public static int broadcast(MinecraftServer server) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) {
            return 0;
        }
        List<CustomPayload> payloads = payloads();
        for (ServerPlayerEntity player : players) {
            send(player, payloads);
        }
        return players.size();
    }
}
