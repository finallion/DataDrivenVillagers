package com.lion.datadrivenvillagers.forge.mixin;

import com.lion.datadrivenvillagers.profession.JobSiteTag;

import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;
import java.util.Map;

/// Forge wraps the point of interest registry and binds its tags in an override that never calls the
/// vanilla method, so the common hook on `SimpleRegistry` does not see this registry.
@Mixin(targets = "net.minecraftforge.registries.NamespacedWrapper")
public abstract class NamespacedWrapperMixin<T> {

    @ModifyVariable(method = "populateTags", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Map<TagKey<T>, List<RegistryEntry<T>>> datadrivenvillagers$addJobSites(
            Map<TagKey<T>, List<RegistryEntry<T>>> tags) {
        @SuppressWarnings("unchecked")
        Registry<T> self = (Registry<T>) (Object) this;
        return JobSiteTag.withJobSites(self.getKey(), tags);
    }
}
