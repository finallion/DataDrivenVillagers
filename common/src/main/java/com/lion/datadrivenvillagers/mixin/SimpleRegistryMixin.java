package com.lion.datadrivenvillagers.mixin;

import com.lion.datadrivenvillagers.DataDrivenVillagers;
import com.lion.datadrivenvillagers.profession.ProfessionRegistry;
import com.lion.datadrivenvillagers.type.TypeLoader;
import com.lion.datadrivenvillagers.type.TypeRegistry;

import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.PointOfInterestTypeTags;
import net.minecraft.registry.tag.TagGroupLoader;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.poi.PointOfInterestType;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// Appends our points of interest to `minecraft:acquirable_job_site` while the tag is bound. The job
/// site sensor only considers POIs in that tag, and the ids do not exist before the config folder is
/// read, so a datapack tag file cannot carry them. Two hooks: `startTagReload` is the datapack path
/// that reaches static registries, `setEntries` covers initial load and network sync.
@Mixin(SimpleRegistry.class)
public abstract class SimpleRegistryMixin<T> {

    @Shadow
    public abstract RegistryKey<? extends Registry<T>> getKey();

    @ModifyVariable(method = "startTagReload", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private TagGroupLoader.RegistryTags<T> datadrivenvillagers$addJobSitesOnReload(
            TagGroupLoader.RegistryTags<T> registryTags) {
        if (!isPointOfInterestRegistry() || ProfessionRegistry.poiEntries().isEmpty()) {
            return registryTags;
        }

        Map<TagKey<T>, List<RegistryEntry<T>>> tags = new HashMap<>(registryTags.tags());
        TagKey<T> jobSites = acquirableJobSite();
        List<RegistryEntry<T>> merged = new ArrayList<>(tags.getOrDefault(jobSites, List.of()));
        if (append(merged) == 0) {
            return registryTags;
        }

        tags.put(jobSites, List.copyOf(merged));
        return new TagGroupLoader.RegistryTags<>(registryTags.key(), tags);
    }

    /// Mixin captures target arguments all or nothing, so the full parameter list follows the value.
    @ModifyVariable(method = "setEntries", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private List<RegistryEntry<T>> datadrivenvillagers$addJobSites(List<RegistryEntry<T>> value,
                                                                  TagKey<T> tag,
                                                                  List<RegistryEntry<T>> entries) {
        if (!isPointOfInterestRegistry() || !PointOfInterestTypeTags.ACQUIRABLE_JOB_SITE.equals(tag)) {
            return entries;
        }

        List<RegistryEntry<T>> merged = new ArrayList<>(entries);
        return append(merged) == 0 ? entries : List.copyOf(merged);
    }

    /// Read-only: hands the members of each biome tag to the type loader. Biomes are a datapack
    /// registry, so they arrive through `setEntries`, not `startTagReload`.
    @Inject(method = "setEntries", at = @At("HEAD"))
    private void datadrivenvillagers$readBiomeTags(TagKey<T> tag, List<RegistryEntry<T>> entries,
                                                   CallbackInfo ci) {
        if (!RegistryKeys.BIOME.equals(getKey()) || TypeRegistry.withBiomeTags().isEmpty()) {
            return;
        }

        List<Identifier> biomes = new ArrayList<>();
        for (RegistryEntry<T> entry : entries) {
            entry.getKey().ifPresent(key -> biomes.add(key.getValue()));
        }
        TypeLoader.claimTaggedBiomes(tag.id(), biomes);
    }

    @Unique
    private boolean isPointOfInterestRegistry() {
        return RegistryKeys.POINT_OF_INTEREST_TYPE.equals(getKey());
    }

    @SuppressWarnings("unchecked")
    @Unique
    private TagKey<T> acquirableJobSite() {
        return (TagKey<T>) PointOfInterestTypeTags.ACQUIRABLE_JOB_SITE;
    }

    /// @return how many were new; a second pass over an already patched list adds nothing
    @SuppressWarnings("unchecked")
    @Unique
    private int append(List<RegistryEntry<T>> target) {
        int added = 0;
        for (RegistryEntry<PointOfInterestType> entry : ProfessionRegistry.poiEntries()) {
            RegistryEntry<T> cast = (RegistryEntry<T>) entry;
            if (!target.contains(cast)) {
                target.add(cast);
                added++;
            }
        }

        if (added > 0) {
            DataDrivenVillagers.LOGGER.info("Added {} job site(s) to {}", added,
                    PointOfInterestTypeTags.ACQUIRABLE_JOB_SITE.id());
        }
        return added;
    }
}
