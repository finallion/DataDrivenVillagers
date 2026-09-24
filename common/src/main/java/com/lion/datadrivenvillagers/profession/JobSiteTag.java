package com.lion.datadrivenvillagers.profession;

import com.lion.datadrivenvillagers.DataDrivenVillagers;

import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.PointOfInterestTypeTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.world.poi.PointOfInterestType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// Appends our points of interest to `minecraft:acquirable_job_site` while the tag is bound. The job
/// site sensor only considers POIs in that tag, and the ids do not exist before the config folder is
/// read, so a datapack tag file cannot carry them. Forge binds the tags of its own registries in a
/// separate override, so each loader calls this from its own binding point.
public final class JobSiteTag {

    private JobSiteTag() {
    }

    @SuppressWarnings("unchecked")
    public static <T> Map<TagKey<T>, List<RegistryEntry<T>>> withJobSites(
            RegistryKey<? extends Registry<T>> registry, Map<TagKey<T>, List<RegistryEntry<T>>> tags) {
        if (!RegistryKeys.POINT_OF_INTEREST_TYPE.equals(registry) || ProfessionRegistry.poiEntries().isEmpty()) {
            return tags;
        }

        TagKey<T> jobSites = (TagKey<T>) PointOfInterestTypeTags.ACQUIRABLE_JOB_SITE;
        List<RegistryEntry<T>> merged = new ArrayList<>(tags.getOrDefault(jobSites, List.of()));
        int added = 0;
        for (RegistryEntry<PointOfInterestType> entry : ProfessionRegistry.poiEntries()) {
            RegistryEntry<T> cast = (RegistryEntry<T>) entry;
            if (!merged.contains(cast)) {
                merged.add(cast);
                added++;
            }
        }
        if (added == 0) {
            return tags;
        }

        DataDrivenVillagers.LOGGER.info("Added {} job site(s) to {}", added,
                PointOfInterestTypeTags.ACQUIRABLE_JOB_SITE.id());
        Map<TagKey<T>, List<RegistryEntry<T>>> patched = new HashMap<>(tags);
        patched.put(jobSites, List.copyOf(merged));
        return patched;
    }
}
