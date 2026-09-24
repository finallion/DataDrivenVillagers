package com.lion.datadrivenvillagers.mixin;

import com.lion.datadrivenvillagers.profession.JobSiteTag;
import com.lion.datadrivenvillagers.type.TypeLoader;
import com.lion.datadrivenvillagers.type.TypeRegistry;

import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// Both hooks sit on `populateTags` because it is the only point that sees every tag binding: initial
/// load, datapack reload and network sync. A registry that Forge wraps skips this method, so the
/// forge module needs its own hook for job sites.
@Mixin(SimpleRegistry.class)
public abstract class SimpleRegistryMixin<T> {

    @Shadow
    public abstract RegistryKey<? extends Registry<T>> getKey();

    @ModifyVariable(method = "populateTags", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Map<TagKey<T>, List<RegistryEntry<T>>> datadrivenvillagers$addJobSites(
            Map<TagKey<T>, List<RegistryEntry<T>>> tags) {
        return JobSiteTag.withJobSites(getKey(), tags);
    }

    /// Read-only: hands the members of each biome tag to the type loader.
    @Inject(method = "populateTags", at = @At("HEAD"))
    private void datadrivenvillagers$readBiomeTags(Map<TagKey<T>, List<RegistryEntry<T>>> tags,
                                                   CallbackInfo ci) {
        if (!RegistryKeys.BIOME.equals(getKey()) || TypeRegistry.withBiomeTags().isEmpty()) {
            return;
        }

        for (Map.Entry<TagKey<T>, List<RegistryEntry<T>>> tag : tags.entrySet()) {
            List<Identifier> biomes = new ArrayList<>();
            for (RegistryEntry<T> entry : tag.getValue()) {
                entry.getKey().ifPresent(key -> biomes.add(key.getValue()));
            }
            TypeLoader.claimTaggedBiomes(tag.getKey().id(), biomes);
        }
    }
}
