package com.lion.datadrivenvillagers.network;

import com.lion.datadrivenvillagers.DataDrivenVillagers;
import com.lion.datadrivenvillagers.profession.HatKind;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.Locale;
import java.util.Optional;

/// Server to client: how one profession or villager type looks. Sent on join and after `/ddv reload`,
/// one packet per definition because vanilla caps a custom payload at one megabyte. Only what the
/// renderer reads travels: hat and image; a profession not registered on the client cannot be created
/// by a packet.
///
/// @param kind          which registry the renderer asks
/// @param target        the id the renderer asks with: the vanilla id for an override, ours otherwise
/// @param definition    the file's own id, where the image is served from
/// @param hat           hat layer overlap, meaningless for a type
/// @param texture       resource pack identifier, absent when png or nothing is sent
/// @param png           the image bytes, absent when an identifier is sent or the file had none
/// @param zombieTexture the zombie villager's identifier, absent means it wears `texture`
/// @param zombiePng     the zombie villager's image, absent means it wears `png`
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
