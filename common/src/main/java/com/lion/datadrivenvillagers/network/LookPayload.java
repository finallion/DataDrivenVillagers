package com.lion.datadrivenvillagers.network;

import com.lion.datadrivenvillagers.DataDrivenVillagers;
import com.lion.datadrivenvillagers.profession.HatKind;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.Locale;
import java.util.Optional;

/// Server to client: how one profession or villager type looks. Sent on join and after `/ddv reload`,
/// one packet per definition since vanilla caps a custom payload at one megabyte. Only what the
/// renderer reads travels: hat (meaningless for a type) and image; a profession not registered on
/// the client cannot be created by a packet.
///
/// `target` is the renderer's lookup id, the vanilla id for an override; `definition` is the file's own id.
/// An absent `zombieTexture` or `zombiePng` means the zombie villager wears the matching villager one.
public record LookPayload(
        Kind kind,
        Identifier target,
        Identifier definition,
        HatKind hat,
        Optional<Identifier> texture,
        Optional<byte[]> png,
        Optional<Identifier> zombieTexture,
        Optional<byte[]> zombiePng
) implements CustomPayload {

    public enum Kind {
        PROFESSION, TYPE;

        public String lower() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final Id<LookPayload> ID = new Id<>(DataDrivenVillagers.id("look"));

    public static final PacketCodec<RegistryByteBuf, LookPayload> CODEC =
            PacketCodec.of(LookPayload::write, LookPayload::read);

    private static void write(LookPayload payload, RegistryByteBuf buf) {
        buf.writeEnumConstant(payload.kind());
        buf.writeIdentifier(payload.target());
        buf.writeIdentifier(payload.definition());
        buf.writeEnumConstant(payload.hat());
        buf.writeOptional(payload.texture(), PacketByteBuf::writeIdentifier);
        buf.writeOptional(payload.png(), (b, bytes) -> b.writeByteArray(bytes));
        buf.writeOptional(payload.zombieTexture(), PacketByteBuf::writeIdentifier);
        buf.writeOptional(payload.zombiePng(), (b, bytes) -> b.writeByteArray(bytes));
    }

    private static LookPayload read(RegistryByteBuf buf) {
        return new LookPayload(
                buf.readEnumConstant(Kind.class),
                buf.readIdentifier(),
                buf.readIdentifier(),
                buf.readEnumConstant(HatKind.class),
                buf.readOptional(PacketByteBuf::readIdentifier),
                buf.readOptional(b -> b.readByteArray()),
                buf.readOptional(PacketByteBuf::readIdentifier),
                buf.readOptional(b -> b.readByteArray()));
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
