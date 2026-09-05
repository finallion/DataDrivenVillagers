package com.lion.datadrivenvillagers.mixin.client;

import com.lion.datadrivenvillagers.TexturedDefinition;
import com.lion.datadrivenvillagers.client.RuntimeTextures;
import com.lion.datadrivenvillagers.network.SyncedLooks;
import com.lion.datadrivenvillagers.profession.HatKind;
import com.lion.datadrivenvillagers.profession.ProfessionLoader;
import com.lion.datadrivenvillagers.profession.ProfessionRegistry;
import com.lion.datadrivenvillagers.type.TypeLoader;
import com.lion.datadrivenvillagers.type.TypeRegistry;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.client.render.entity.feature.VillagerClothingFeatureRenderer;
import net.minecraft.client.render.entity.feature.VillagerResourceMetadata;
import net.minecraft.registry.DefaultedRegistry;
import net.minecraft.util.Identifier;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;
import java.util.Optional;

/// Texture hook: serves an explicit identifier or a runtime-registered png (server sync first, the
/// client's own folder second). Hat hook: vanilla reads the hat type from a `.png.mcmeta` beside the
/// texture, and a runtime texture has none.
@Mixin(VillagerClothingFeatureRenderer.class)
public abstract class VillagerClothingFeatureRendererMixin {

    private static final String PROFESSION = "profession";
    private static final String TYPE = "type";

    /// "villager" or "zombie_villager", set at construction.
    @Shadow
    @Final
    private String entityType;

    /// Serves both layers: `type` is the biome clothing underneath, `profession` the job on top.
    /// Vanilla builds the path as `textures/entity/<entity>/<layer>/<name>.png`.
    @Inject(method = "findTexture(Ljava/lang/String;Lnet/minecraft/util/Identifier;)Lnet/minecraft/util/Identifier;",
            at = @At("HEAD"), cancellable = true)
    private void datadrivenvillagers$overrideTexture(String layer, Identifier id,
                                                     CallbackInfoReturnable<Identifier> cir) {
        Optional<Identifier> served;
        if (PROFESSION.equals(layer)) {
            Optional<SyncedLooks.Look> look = SyncedLooks.profession(id);
            served = datadrivenvillagers$synced(look)
                    .or(() -> datadrivenvillagers$folderMayAnswer(look)
                            ? ProfessionRegistry.get(id).flatMap(definition ->
                                    datadrivenvillagers$local(definition, ProfessionLoader.directory()))
                            : Optional.empty());
        } else if (TYPE.equals(layer)) {
            Optional<SyncedLooks.Look> look = SyncedLooks.type(id);
            served = datadrivenvillagers$synced(look)
                    .or(() -> datadrivenvillagers$folderMayAnswer(look)
                            ? TypeRegistry.get(id).flatMap(definition ->
                                    datadrivenvillagers$local(definition, TypeLoader.directory()))
                            : Optional.empty());
        } else {
            return;
        }
        served.ifPresent(cir::setReturnValue);
    }

    @Unique
    private Optional<Identifier> datadrivenvillagers$synced(Optional<SyncedLooks.Look> look) {
        if (look.isEmpty()) {
            return Optional.empty();
        }
        Optional<Identifier> explicit = look.get().textureFor(entityType);
        if (explicit.isPresent()) {
            return explicit;
        }
        return RuntimeTextures.fromBytes(look.get(), entityType);
    }

    /// The local folder answers before any sync, and for a synced definition without an image (a png
    /// too large to send). Never for an id the server did not mention.
    @Unique
    private static boolean datadrivenvillagers$folderMayAnswer(Optional<SyncedLooks.Look> look) {
        return look.isPresent() || !SyncedLooks.active();
    }

    /// The id comes from the definition, not from vanilla's derivation: an override applies to
    /// `minecraft:farmer` while its image lives in our namespace. An unreadable file falls through to
    /// vanilla.
    @Unique
    private Optional<Identifier> datadrivenvillagers$local(TexturedDefinition definition, Path folder) {
        Optional<Identifier> explicit = definition.textureFor(entityType);
        if (explicit.isPresent()) {
            return explicit;
        }
        return RuntimeTextures.fromFile(definition, folder, entityType);
    }

    @Inject(method = "getHatType", at = @At("HEAD"), cancellable = true)
    private <K> void datadrivenvillagers$overrideHat(Object2ObjectMap<K, VillagerResourceMetadata.HatType> map,
                                                     String type, DefaultedRegistry<K> registry, K value,
                                                     CallbackInfoReturnable<VillagerResourceMetadata.HatType> cir) {
        if (!PROFESSION.equals(type)) {
            return;
        }
        Identifier id = registry.getId(value);
        if (id == null) {
            return;
        }
        Optional<HatKind> hat = SyncedLooks.profession(id).map(SyncedLooks.Look::hat);
        if (hat.isEmpty() && !SyncedLooks.active()) {
            hat = ProfessionRegistry.get(id).map(definition -> definition.hat());
        }
        hat.map(VillagerClothingFeatureRendererMixin::toHatType).ifPresent(cir::setReturnValue);
    }

    private static VillagerResourceMetadata.HatType toHatType(HatKind kind) {
        return switch (kind) {
            case NONE -> VillagerResourceMetadata.HatType.NONE;
            case PARTIAL -> VillagerResourceMetadata.HatType.PARTIAL;
            case FULL -> VillagerResourceMetadata.HatType.FULL;
        };
    }
}
