package com.lion.datadrivenvillagers.mixin;

import com.lion.datadrivenvillagers.profession.JobSiteTag;

import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;
import java.util.Map;

/// Sits on `populateTags`, the only point that sees every tag binding: initial load, datapack reload
/// and network sync. Forge wraps this registry and skips the method, so the forge module binds its
/// job sites through its own hook instead.
@Mixin(SimpleRegistry.class)
public abstract class SimpleRegistryMixin<T> {

    @Shadow
    public abstract RegistryKey<? extends Registry<T>> getKey();

    @ModifyVariable(method = "populateTags", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Map<TagKey<T>, List<RegistryEntry<T>>> datadrivenvillagers$addJobSites(
            Map<TagKey<T>, List<RegistryEntry<T>>> tags) {
        return JobSiteTag.withJobSites(getKey(), tags);
    }
}
