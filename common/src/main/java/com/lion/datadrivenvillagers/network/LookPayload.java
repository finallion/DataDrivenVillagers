package com.lion.datadrivenvillagers.network;

import com.lion.datadrivenvillagers.DataDrivenVillagers;
import com.lion.datadrivenvillagers.profession.HatKind;

import net.minecraft.network.PacketByteBuf;
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
) implements Payload {

    public enum Kind {
        PROFESSION, TYPE;

        public String lower() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final Identifier ID = DataDrivenVillagers.id("look");

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeEnumConstant(kind);
        buf.writeIdentifier(target);
        buf.writeIdentifier(definition);
        buf.writeEnumConstant(hat);
        buf.writeOptional(texture, PacketByteBuf::writeIdentifier);
        buf.writeOptional(png, (b, bytes) -> b.writeByteArray(bytes));
        buf.writeOptional(zombieTexture, PacketByteBuf::writeIdentifier);
        buf.writeOptional(zombiePng, (b, bytes) -> b.writeByteArray(bytes));
    }

    public static LookPayload read(PacketByteBuf buf) {
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
}
