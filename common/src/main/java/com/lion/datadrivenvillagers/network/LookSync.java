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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// Server side of look sync: builds the packets from the registries and the pngs on disk, fresh on
/// every join and reload, no cache.
public final class LookSync {

    /// 3-byte VarInt frame limit; vanilla's 1 MB cap covers only payloads the receiver does not know.
    static final int MAX_PACKET_BYTES = 2_097_151;

    /// One `LookPayload` carries two images, so this caps each one small enough that both still fit.
    public static final int MAX_PNG_BYTES = 900_000;

    /// Ids, hat, the optionals and the frame, generously.
    static final int PACKET_HEADROOM = 16_384;

    private LookSync() {
    }

    public static List<CustomPayload> payloads() {
        List<LookPayload> looks = new ArrayList<>();
        Path professions = ProfessionLoader.directory();
        for (ProfessionDefinition definition : ProfessionRegistry.ordered()) {
            looks.add(look(LookPayload.Kind.PROFESSION, definition.target(), definition, definition.hat(), professions));
        }
        Path types = TypeLoader.directory();
        for (TypeDefinition definition : TypeRegistry.ordered()) {
            looks.add(look(LookPayload.Kind.TYPE, definition.id(), definition, HatKind.NONE, types));
        }

        List<CustomPayload> payloads = new ArrayList<>();
        payloads.add(new LooksBeginPayload(looks.size()));
        payloads.addAll(looks);
        return payloads;
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
            long size = Files.size(png);
            if (size > MAX_PNG_BYTES) {
                DataDrivenVillagers.LOGGER.warn("{} is {} bytes, too large to send to players; they will "
                        + "use their own copy of {} if they have one", png, size, id);
                return Optional.empty();
            }
            return Optional.of(Files.readAllBytes(png));
        } catch (IOException e) {
            DataDrivenVillagers.LOGGER.error("Could not read {} to send it to players", png, e);
            return Optional.empty();
        }
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
