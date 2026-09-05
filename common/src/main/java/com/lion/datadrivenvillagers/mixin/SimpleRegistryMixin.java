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
/// read, so a datapack tag file cannot carry them. `populateTags` is the single binding point and
/// covers all three paths: initial load, datapack reload and network sync.
@Mixin(SimpleRegistry.class)
public abstract class SimpleRegistryMixin<T> {

    @Shadow
    public abstract RegistryKey<? extends Registry<T>> getKey();

    @ModifyVariable(method = "populateTags", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Map<TagKey<T>, List<RegistryEntry<T>>> datadrivenvillagers$addJobSites(
            Map<TagKey<T>, List<RegistryEntry<T>>> tags) {
        if (!isPointOfInterestRegistry() || ProfessionRegistry.poiEntries().isEmpty()) {
            return tags;
        }

        TagKey<T> jobSites = acquirableJobSite();
        List<RegistryEntry<T>> merged = new ArrayList<>(tags.getOrDefault(jobSites, List.of()));
        if (append(merged) == 0) {
            return tags;
        }

        Map<TagKey<T>, List<RegistryEntry<T>>> patched = new HashMap<>(tags);
        patched.put(jobSites, List.copyOf(merged));
        return patched;
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
