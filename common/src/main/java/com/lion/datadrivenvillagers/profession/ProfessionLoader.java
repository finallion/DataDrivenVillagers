package com.lion.datadrivenvillagers.profession;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.lion.datadrivenvillagers.DataDrivenVillagers;
import com.lion.datadrivenvillagers.DefinitionParseException;
import com.lion.datadrivenvillagers.JsonFolder;
import com.lion.datadrivenvillagers.LoadError;
import com.lion.datadrivenvillagers.ReloadOutcome;
import com.lion.datadrivenvillagers.mixin.PointOfInterestTypesAccessor;
import com.lion.datadrivenvillagers.platform.ConfigDirectory;
import com.lion.datadrivenvillagers.platform.PlatformInfo;
import com.lion.datadrivenvillagers.platform.RegistryHelper;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.poi.PointOfInterestType;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/// Reads every profession file during startup and registers a point of interest plus a villager
/// profession for each. Three phases, because NeoForge hands out one RegisterEvent per registry and
/// the block registry is only complete when the point of interest registry comes up. A broken file is
/// logged and skipped, never stops the others.
public final class ProfessionLoader {

    private static final String FOLDER = "professions";
    private static final String EXTENSION = ".json";

    /// Refreshed by every reload, so a later server start retries overrides against what is on disk now.
    private static final List<ProfessionDefinition> PARSED = new ArrayList<>();
    private static final Map<Identifier, PointOfInterestType> POINTS_OF_INTEREST = new LinkedHashMap<>();
    private static final Set<Identifier> POI_FAILED = new LinkedHashSet<>();

    /// Override files rejected only because their target profession did not exist yet; retried at server start.
    private static final Set<String> AWAITING_TARGET = new LinkedHashSet<>();

    /// Blocks already reported as an accepted natural-block workstation this load; every caller shares one log line.
    private static final Set<Identifier> LOGGED_ALLOWED_ANYWAY = new LinkedHashSet<>();

    private ProfessionLoader() {
    }

    public static Path directory() {
        return ConfigDirectory.getConfigDirectory().resolve(DataDrivenVillagers.MOD_ID).resolve(FOLDER);
    }

    private static boolean prepared;

    /// Fabric orders its phases so a rejected point of interest keeps its profession unregistered.
    public static void loadAll() {
        prepare();
        registerNewPointsOfInterest();
        registerProfessions();
        applyOverrides();
        logLoaded();
    }

    /// Idempotent: NeoForge fires RegisterEvent for professions before points of interest.
    public static void prepare() {
        if (prepared) {
            return;
        }
        prepared = true;
        parseInto(PARSED);
        buildPointsOfInterest();
    }

    /// Phase 1: disk to definitions. No registry access, so a reload can run it again into its own list.
    private static void parseInto(List<ProfessionDefinition> target) {
        Path dir = directory();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            DataDrivenVillagers.LOGGER.error("Could not create {}, no professions will be loaded", dir, e);
            return;
        }

        ExampleProfession.writeIfFolderIsEmpty(dir);

        JsonFolder.forEachFile(dir, "professions", file -> parseOne(file, target));
    }

    /// Phase 2: needs a complete block registry; drops definitions with a missing or claimed workstation.
    private static void buildPointsOfInterest() {
        // Claims by an earlier file in this same pass; POI_STATES_TO_TYPE has none of them yet.
        Map<BlockState, Identifier> claimedByEarlierFiles = new LinkedHashMap<>();

        Iterator<ProfessionDefinition> iterator = PARSED.iterator();
        while (iterator.hasNext()) {
            ProfessionDefinition definition = iterator.next();
            if (definition.isOverride()) {
                continue;
            }
            try {
                PointOfInterestType poi = createPointOfInterest(definition, claimedByEarlierFiles);
                POINTS_OF_INTEREST.put(definition.id(), poi);
                for (BlockState state : poi.blockStates()) {
                    claimedByEarlierFiles.put(state, definition.id());
                }
            } catch (Exception e) {
                reject(definition.name() + EXTENSION, e);
                iterator.remove();
            }
        }
    }

    /// Needs only the job site instance, not its registration: the predicate compares by identity.
    public static void registerProfessions() {
        prepare();
        for (ProfessionDefinition definition : PARSED) {
            if (definition.isOverride() || POI_FAILED.contains(definition.id())) {
                continue;
            }
            PointOfInterestType poi = POINTS_OF_INTEREST.get(definition.id());
            if (poi == null) {
                continue;
            }
            try {
                RegistryHelper.registerVillagerProfession(definition.id(), createProfession(definition, poi));
            } catch (Exception e) {
                reject(definition.name() + EXTENSION, e);
            }
        }
    }

    /// NeoForge's point of interest event: new job sites first, then overrides.
    public static void registerPointsOfInterest() {
        prepare();
        registerNewPointsOfInterest();
        applyOverrides();
        logLoaded();
    }

    /// Runs before applyOverrides, so an override cannot take a block of a new profession.
    private static void registerNewPointsOfInterest() {
        for (ProfessionDefinition definition : PARSED) {
            if (!definition.isOverride()) {
                registerOnePointOfInterest(definition);
            }
        }
    }

    private static void applyOverrides() {
        for (ProfessionDefinition definition : PARSED) {
            if (definition.isOverride()) {
                String file = definition.name() + EXTENSION;
                try {
                    applyOverride(definition);
                    AWAITING_TARGET.remove(file);
                } catch (Exception e) {
                    trackIfAwaitingTarget(definition, file);
                    reject(file, e);
                }
            }
        }
    }

    /// Only a missing target profession is worth a retry; every other rejection reason stays as it is.
    private static void trackIfAwaitingTarget(ProfessionDefinition definition, String file) {
        if (Registries.VILLAGER_PROFESSION.getOptionalValue(definition.target()).isEmpty()) {
            AWAITING_TARGET.add(file);
        } else {
            AWAITING_TARGET.remove(file);
        }
    }

    private static void logLoaded() {
        DataDrivenVillagers.LOGGER.info("Loaded {} villager profession(s) from {}, {} file(s) rejected",
                ProfessionRegistry.definitions().size(), directory(), ProfessionRegistry.errors().size());
    }

    /// Registers one point of interest, then fills POI_STATES_TO_TYPE, which needs the registry entry.
    private static void registerOnePointOfInterest(ProfessionDefinition definition) {
        PointOfInterestType poi = POINTS_OF_INTEREST.get(definition.id());
        if (poi == null) {
            return;
        }

        // Only a foreign mod can own a state here; buildPointsOfInterest resolves clashes between DDV files.
        Optional<String> conflict = conflictReason(poi);
        if (conflict.isPresent()) {
            POI_FAILED.add(definition.id());
            reject(definition.name() + EXTENSION, new DefinitionParseException(conflict.get()));
            return;
        }

        try {
            RegistryHelper.registerPointOfInterestType(definition.id(), poi);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Registering the point of interest type for " + definition.name() + EXTENSION + " failed", e);
        }

        try {
            RegistryEntry<PointOfInterestType> entry = Registries.POINT_OF_INTEREST_TYPE.getEntry(poi);

            // Vanilla fills this map in static init before mod POIs exist; the sensor reads it, not the registry.
            for (BlockState state : poi.blockStates()) {
                PointOfInterestTypesAccessor.ddv$poiStatesToType().put(state, entry);
            }
            ProfessionRegistry.add(definition, entry);
            warnIfTextureless(definition);
        } catch (Exception e) {
            POI_FAILED.add(definition.id());
            reject(definition.name() + EXTENSION, e);
        }
    }

    /// Names the one workstation block another job site already owns, if any.
    private static Optional<String> conflictReason(PointOfInterestType poi) {
        for (BlockState state : poi.blockStates()) {
            RegistryEntry<PointOfInterestType> existing = PointOfInterestTypesAccessor.ddv$poiStatesToType().get(state);
            if (existing != null) {
                String owner = existing.getKey().map(key -> key.getValue().toString()).orElse("another job site");
                return Optional.of(Registries.BLOCK.getId(state.getBlock()) + " is already a job site of " + owner);
            }
        }
        return Optional.empty();
    }

    /// Rereads every file; fields baked into vanilla's frozen record need a restart to apply.
    public static List<ReloadOutcome> reload(MinecraftServer server) {
        ProfessionRegistry.clearErrors();
        LOGGED_ALLOWED_ANYWAY.clear();
        List<ProfessionDefinition> fresh = new ArrayList<>();
        parseInto(fresh);

        List<ReloadOutcome> outcomes = new ArrayList<>();
        Map<Identifier, ProfessionDefinition> previous = new LinkedHashMap<>(ProfessionRegistry.definitions());

        // A brain keeps the plan object it was built with, so these villagers need a rebuild.
        Set<Identifier> rebriefed = new LinkedHashSet<>();

        for (ProfessionDefinition next : fresh) {
            String file = next.name() + EXTENSION;
            ProfessionDefinition old = previous.remove(next.target());
            if (old == null) {
                if (next.isOverride()) {
                    // A new override needs no restart: its target exists and everything it changes is a lookup.
                    if (applyNewOverride(file, next)) {
                        outcomes.add(ReloadOutcome.updated(file, newOverrideDetail(next)));
                        if (needsBrain(next)) {
                            rebriefed.add(next.target());
                        }
                    }
                    continue;
                }
                if (!workstationsUsable(file, next)) {
                    // Rejected with startup's words, instead of reported as a new profession.
                    continue;
                }
                outcomes.add(ReloadOutcome.restart(file,
                        "new profession, and a profession is registered before any world exists"));
            } else if (old.equals(next)) {
                outcomes.add(ReloadOutcome.unchanged(file));
            } else {
                outcomes.add(apply(file, old, next));
                if (brainDiffers(old, next)) {
                    rebriefed.add(next.target());
                }
            }
        }

        // A file that failed to parse is still on disk: its profession keeps the last good definition.
        Map<Identifier, ProfessionDefinition> kept = keptDespiteRejection(previous, ProfessionRegistry.errors());
        previous.keySet().removeAll(kept.keySet());

        for (ProfessionDefinition gone : previous.values()) {
            releaseWorkstations(gone);
            ProfessionRegistry.remove(gone.target());
            if (needsBrain(gone)) {
                // Its villagers get vanilla's plan and routine back.
                rebriefed.add(gone.target());
            }
            outcomes.add(ReloadOutcome.restart(gone.name() + EXTENSION,
                    "file is gone, its blocks were given back but the profession itself stays"));
        }

        for (LoadError error : ProfessionRegistry.errors()) {
            outcomes.add(ReloadOutcome.rejected(error.file(), rejectionDetail(error, kept)));
        }

        PARSED.clear();
        PARSED.addAll(fresh);

        // A file no longer on disk cannot be retried, so it must not linger in the tracking set.
        Set<String> freshOverrideFiles = new LinkedHashSet<>();
        for (ProfessionDefinition definition : fresh) {
            if (definition.isOverride()) {
                freshOverrideFiles.add(definition.name() + EXTENSION);
            }
        }
        AWAITING_TARGET.retainAll(freshOverrideFiles);

        DataDrivenVillagers.newGeneration();

        // After newGeneration(): the VillagerSchedules cache drops old plans only once the counter moved.
        int villagers = rebriefed.isEmpty() ? 0 : rebrief(server, rebriefed);
        if (villagers > 0) {
            // "(brains)" is not a file name and must not look like one.
            outcomes.add(ReloadOutcome.updated("(brains)",
                    villagers + " villager(s) already in the world had their brain rebuilt for the new plan or routine"));
        }
        return outcomes;
    }

    /// Rejected files, keyed like `previous`; matched by name since a failed parse has no id.
    static Map<Identifier, ProfessionDefinition> keptDespiteRejection(
            Map<Identifier, ProfessionDefinition> previous, List<LoadError> errors) {
        Set<String> rejectedFiles = new LinkedHashSet<>();
        for (LoadError error : errors) {
            rejectedFiles.add(error.file());
        }
        Map<Identifier, ProfessionDefinition> kept = new LinkedHashMap<>();
        for (Map.Entry<Identifier, ProfessionDefinition> entry : previous.entrySet()) {
            if (rejectedFiles.contains(entry.getValue().name() + EXTENSION)) {
                kept.put(entry.getKey(), entry.getValue());
            }
        }
        return kept;
    }

    /// The rejection, plus whether an earlier version of the profession is still in effect.
    static String rejectionDetail(LoadError error, Map<Identifier, ProfessionDefinition> kept) {
        for (ProfessionDefinition definition : kept.values()) {
            if ((definition.name() + EXTENSION).equals(error.file())) {
                return error.reason() + " - the version loaded before this reload stays in effect";
            }
        }
        return error.reason();
    }

    /// Startup's block check for a profession the reload sees for the first time; the instance built here is discarded.
    private static boolean workstationsUsable(String file, ProfessionDefinition definition) {
        try {
            createPointOfInterest(definition, Map.of());
            return true;
        } catch (Exception e) {
            reject(file, e);
            return false;
        }
    }

    /// Whether a definition changes what `initBrain` builds; `flees_from`/`villages` need no rebuild, asked every tick.
    private static boolean needsBrain(ProfessionDefinition definition) {
        return definition.schedule().isPresent() || definition.workBehaviour() != WorkBehaviour.STATION
                || definition.attack().isPresent() || definition.health().isPresent();
    }

    private static boolean brainDiffers(ProfessionDefinition old, ProfessionDefinition next) {
        return !old.schedule().equals(next.schedule()) || old.workBehaviour() != next.workBehaviour()
                || !old.attack().equals(next.attack()) || !old.health().equals(next.health());
    }

    /// Returns false when rejected; the reason is already in the error list, so the caller adds no outcome of its own.
    private static boolean applyNewOverride(String file, ProfessionDefinition definition) {
        try {
            applyOverride(definition);
            AWAITING_TARGET.remove(file);
            return true;
        } catch (Exception e) {
            trackIfAwaitingTarget(definition, file);
            reject(file, e);
            return false;
        }
    }

    private static String newOverrideDetail(ProfessionDefinition definition) {
        List<String> said = new ArrayList<>();
        said.add("new override of " + definition.target() + ", applied");
        if (!definition.addWorkstations().isEmpty()) {
            said.add(definition.addWorkstations().size() + " block(s) offered to its job site");
        }
        return String.join(", ", said);
    }

    /// Collected first, not rebuilt while iterating entities; rebuilds via vanilla's `reinitializeBrain`.
    private static int rebrief(MinecraftServer server, Set<Identifier> professions) {
        int touched = 0;
        for (ServerWorld world : server.getWorlds()) {
            List<VillagerEntity> affected = new ArrayList<>();
            for (Entity entity : world.iterateEntities()) {
                if (entity instanceof VillagerEntity villager && professions.contains(professionOf(villager))) {
                    affected.add(villager);
                }
            }
            for (VillagerEntity villager : affected) {
                villager.reinitializeBrain(world);
                touched++;
            }
        }
        return touched;
    }

    /// @return null for an unregistered profession, which matches nothing
    private static Identifier professionOf(VillagerEntity villager) {
        return villager.getVillagerData().profession().getKey()
                .map(RegistryKey::getValue)
                .orElse(null);
    }

    private static ReloadOutcome apply(String file, ProfessionDefinition old, ProfessionDefinition next) {
        List<String> applied = new ArrayList<>();
        if (!old.texture().equals(next.texture()) || !old.textureFile().equals(next.textureFile())) {
            applied.add("texture");
        }
        if (!old.zombieTexture().equals(next.zombieTexture())
                || !old.zombieTextureFile().equals(next.zombieTextureFile())) {
            applied.add("zombie_texture");
        }
        if (old.hat() != next.hat()) {
            applied.add("hat");
        }
        if (!old.gift().equals(next.gift())) {
            applied.add("gift");
        }
        if (!old.schedule().equals(next.schedule())) {
            applied.add("schedule");
        }
        if (old.workBehaviour() != next.workBehaviour()) {
            applied.add("work_behaviour");
        }
        if (!old.fears().equals(next.fears())) {
            applied.add(old.fears().replacesVanilla() || next.fears().replacesVanilla() ? "flees_only_from" : "flees_from");
        }
        if (!old.attack().equals(next.attack())) {
            applied.add("attacks");
        }
        if (!old.health().equals(next.health())) {
            applied.add("health");
        }
        if (!old.villages().equals(next.villages())) {
            applied.add("villages");
        }
        String stations = moveWorkstations(old, next);
        if (!stations.isEmpty()) {
            applied.add(stations);
        }

        ProfessionRegistry.replace(next);

        List<String> frozen = frozenFields(old, next);
        if (frozen.isEmpty()) {
            return ReloadOutcome.updated(file, String.join(", ", applied));
        }
        return ReloadOutcome.restart(file, (applied.isEmpty() ? "" : String.join(", ", applied) + "; ")
                + "restart for " + String.join(", ", frozen));
    }

    /// `blockStates()` is frozen once registered; the sensor reads the mutable POI_STATES_TO_TYPE map.
    private static String moveWorkstations(ProfessionDefinition old, ProfessionDefinition next) {
        List<Identifier> before = old.ownBlocks();
        List<Identifier> after = next.ownBlocks();
        // An override's block set is read fresh each time, so a flag-only change can still be applied here.
        boolean revisitForFlag = next.isOverride() && old.allowNaturalBlock() != next.allowNaturalBlock()
                && after.stream().anyMatch(NaturalBlocks.IDS::contains);
        if (before.equals(after) && !revisitForFlag) {
            return "";
        }

        Optional<RegistryEntry<PointOfInterestType>> jobSite = jobSiteFor(next);
        if (jobSite.isEmpty()) {
            return "blocks unchanged, there is no job site to move them to";
        }
        RegistryEntry<PointOfInterestType> poi = jobSite.get();

        int released = 0;
        int claimed = 0;
        List<Identifier> refused = new ArrayList<>();
        for (Identifier blockId : before) {
            if (!after.contains(blockId)) {
                released += releaseBlock(blockId, poi);
            } else if (revisitForFlag && NaturalBlocks.IDS.contains(blockId)) {
                if (next.allowNaturalBlock()) {
                    if (claimBlock(blockId, poi, true)) {
                        claimed++;
                    } else {
                        refused.add(blockId);
                    }
                } else {
                    released += releaseBlock(blockId, poi);
                }
            }
        }
        for (Identifier blockId : after) {
            if (before.contains(blockId)) {
                continue;
            }
            if (claimBlock(blockId, poi, next.allowNaturalBlock())) {
                claimed++;
            } else {
                refused.add(blockId);
            }
        }

        List<String> said = new ArrayList<>();
        if (claimed > 0) {
            said.add(claimed + " block(s) added");
        }
        if (released > 0) {
            said.add(released + " block(s) released");
        }
        if (!refused.isEmpty()) {
            said.add("refused " + refused + ", unknown, unsuitable, or already a job site");
        }
        if (said.isEmpty() && revisitForFlag) {
            return "allow_natural_block changed, no natural block in the list was affected";
        }
        return String.join(", ", said);
    }

    /// Clears only states that still point at this job site.
    private static int releaseBlock(Identifier blockId, RegistryEntry<PointOfInterestType> poi) {
        Optional<Block> block = Registries.BLOCK.getOptionalValue(blockId);
        if (block.isEmpty()) {
            return 0;
        }
        boolean removed = false;
        for (BlockState state : PointOfInterestTypesAccessor.ddv$getStatesOfBlock(block.get())) {
            if (PointOfInterestTypesAccessor.ddv$poiStatesToType().get(state) == poi) {
                PointOfInterestTypesAccessor.ddv$poiStatesToType().remove(state);
                removed = true;
            }
        }
        return removed ? 1 : 0;
    }

    private static boolean claimBlock(Identifier blockId, RegistryEntry<PointOfInterestType> poi,
                                      boolean allowNaturalBlock) {
        Optional<Block> block = Registries.BLOCK.getOptionalValue(blockId);
        if (block.isEmpty() || !check(block.get(), allowNaturalBlock, null, Map.of()).usable()) {
            return false;
        }
        for (BlockState state : PointOfInterestTypesAccessor.ddv$getStatesOfBlock(block.get())) {
            PointOfInterestTypesAccessor.ddv$poiStatesToType().put(state, poi);
        }
        return true;
    }

    private static void releaseWorkstations(ProfessionDefinition gone) {
        Optional<RegistryEntry<PointOfInterestType>> jobSite = jobSiteFor(gone);
        if (jobSite.isEmpty()) {
            return;
        }
        RegistryEntry<PointOfInterestType> poi = jobSite.get();
        for (Identifier blockId : gone.ownBlocks()) {
            releaseBlock(blockId, poi);
        }
    }

    private static Optional<RegistryEntry<PointOfInterestType>> jobSiteFor(ProfessionDefinition definition) {
        if (!definition.isOverride()) {
            return ProfessionRegistry.poiEntry(definition.target());
        }
        return Registries.VILLAGER_PROFESSION.getOptionalValue(definition.target())
                .flatMap(ProfessionLoader::jobSiteOf);
    }

    /// Changed fields that live in a frozen vanilla record. Empty for an override, which never reads them.
    static List<String> frozenFields(ProfessionDefinition old, ProfessionDefinition next) {
        if (next.isOverride()) {
            return List.of();
        }

        List<String> changed = new ArrayList<>();
        if (old.allowNaturalBlock() != next.allowNaturalBlock()) {
            changed.add("allow_natural_block");
        }
        if (!old.displayName().equals(next.displayName())) {
            changed.add("display_name");
        }
        if (!old.workSound().equals(next.workSound())) {
            changed.add("work_sound");
        }
        if (!old.gatherable().equals(next.gatherable())) {
            changed.add("gatherable_items");
        }
        if (!old.secondarySites().equals(next.secondarySites())) {
            changed.add("secondary_job_sites");
        }
        if (old.ticketCount() != next.ticketCount()) {
            changed.add("ticket_count");
        }
        if (old.searchDistance() != next.searchDistance()) {
            changed.add("search_distance");
        }
        return changed;
    }

    /// Extra blocks only need new POI_STATES_TO_TYPE entries, since vanilla's predicate asks `holder.is(poiKey)`.
    private static void applyOverride(ProfessionDefinition definition) {
        Identifier target = definition.target();
        VillagerProfession profession = Registries.VILLAGER_PROFESSION.getOptionalValue(target)
                .orElseThrow(() -> new DefinitionParseException(targetMissingReason(target)));

        // One override per target; otherwise the alphabetically later file silently wins.
        Optional<ProfessionDefinition> other = ProfessionRegistry.get(target)
                .filter(existing -> !existing.name().equals(definition.name()));
        if (other.isPresent()) {
            throw new DefinitionParseException("\"overrides\" names " + target + ", but " + other.get().name()
                    + ".json already overrides it; one file per profession, merge the two");
        }

        RegistryEntry<PointOfInterestType> jobSite = jobSiteOf(profession).orElse(null);
        if (!definition.addWorkstations().isEmpty()) {
            if (jobSite == null) {
                throw new DefinitionParseException("profession " + target
                        + " has no job site of its own, so there is nothing to add blocks to");
            }
            addWorkstations(definition, jobSite);
        }

        ProfessionRegistry.add(definition, jobSite);
    }

    /// Runs at server start. Restores POI states a registry refresh dropped, then retries rejected overrides.
    public static void reapplyAtServerStart() {
        for (ProfessionDefinition definition : ProfessionRegistry.ordered()) {
            Optional<RegistryEntry<PointOfInterestType>> jobSite = jobSiteFor(definition);
            if (jobSite.isEmpty()) {
                continue;
            }
            reclaim(definition.target(), definition.ownBlocks(), jobSite.get(), definition.allowNaturalBlock());
        }
        retryRejectedOverrides();
        for (String warning : naturalBlockWarnings()) {
            DataDrivenVillagers.LOGGER.warn(warning);
        }
    }

    /// Claimed blocks outside the fixed list that still generate in bulk, most likely a modded block.
    public static List<String> naturalBlockWarnings() {
        List<String> warnings = new ArrayList<>();
        for (ProfessionDefinition definition : ProfessionRegistry.ordered()) {
            for (Identifier blockId : definition.ownBlocks()) {
                Optional<Block> block = Registries.BLOCK.getOptionalValue(blockId);
                if (block.isEmpty()) {
                    continue;
                }
                if (StructureBulkBlocks.IDS.contains(blockId)) {
                    warnings.add(definition.target() + " claims " + blockId + ", which generated structures "
                            + "place in bulk; point of interest data and search cost grow with the explored world");
                    continue;
                }
                // The fixed lists cover vanilla; the tags only catch bulk-placed blocks of other mods.
                if (!definition.allowNaturalBlock() && !blockId.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)
                        && generatesInBulk(block.get().getDefaultState())) {
                    warnings.add(definition.target() + " claims " + blockId + ", which world generation "
                            + "likely places in bulk; set \"allow_natural_block\": true if that is intended");
                }
            }
        }
        return warnings;
    }

    private static boolean generatesInBulk(BlockState state) {
        return state.isIn(BlockTags.BASE_STONE_OVERWORLD) || state.isIn(BlockTags.BASE_STONE_NETHER)
                || state.isIn(BlockTags.DIRT) || state.isIn(BlockTags.SAND)
                || state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES)
                || state.isIn(BlockTags.TERRACOTTA);
    }

    /// Fills only states a registry refresh left empty, leaving an already-claimed state as it is.
    private static void reclaim(Identifier owner, List<Identifier> blockIds,
                                RegistryEntry<PointOfInterestType> jobSite, boolean allowNaturalBlock) {
        String jobSiteId = jobSite.getKey().map(key -> key.getValue().toString()).orElse(null);
        int restored = 0;
        for (Identifier blockId : blockIds) {
            Optional<Block> block = Registries.BLOCK.getOptionalValue(blockId);
            if (block.isEmpty()) {
                continue;
            }
            BlockVerdict verdict = check(block.get(), allowNaturalBlock, jobSiteId, Map.of());
            if (!verdict.usable() || !verdict.anyStateFree()) {
                continue;
            }
            for (BlockState state : PointOfInterestTypesAccessor.ddv$getStatesOfBlock(block.get())) {
                PointOfInterestTypesAccessor.ddv$poiStatesToType().putIfAbsent(state, jobSite);
            }
            restored++;
        }
        if (restored > 0) {
            DataDrivenVillagers.LOGGER.info("Restored {} block(s) for {} that a registry refresh had dropped",
                    restored, owner);
        }
    }

    /// Retries only overrides tracked as waiting for a target profession that did not exist yet.
    private static void retryRejectedOverrides() {
        for (ProfessionDefinition definition : PARSED) {
            String file = definition.name() + EXTENSION;
            if (!definition.isOverride() || !AWAITING_TARGET.contains(file)) {
                continue;
            }
            if (ProfessionRegistry.get(definition.target())
                    .filter(held -> held.name().equals(definition.name())).isPresent()) {
                AWAITING_TARGET.remove(file);
                continue;
            }
            try {
                applyOverride(definition);
                AWAITING_TARGET.remove(file);
                ProfessionRegistry.removeError(file);
                DataDrivenVillagers.LOGGER.info(
                        "Override {} applied once the server started, its target profession exists now",
                        definition.target());
            } catch (Exception e) {
                trackIfAwaitingTarget(definition, file);
                ProfessionRegistry.removeError(file);
                reject(file, e);
            }
        }
    }

    private static String targetMissingReason(Identifier target) {
        return "\"overrides\" names " + target + ", which is not a registered profession";
    }

    /// Fields an override reads, asked per villager.
    public static List<String> behaviourFields(ProfessionDefinition definition) {
        List<String> applied = new ArrayList<>();
        if (definition.schedule().isPresent()) {
            applied.add("schedule");
        }
        if (definition.workBehaviour() != WorkBehaviour.STATION) {
            applied.add("work_behaviour");
        }
        if (definition.fears().isSet()) {
            applied.add(definition.fears().replacesVanilla() ? "flees_only_from" : "flees_from");
        }
        if (definition.attack().isPresent()) {
            applied.add("attacks");
        }
        if (definition.health().isPresent()) {
            applied.add("health");
        }
        if (!definition.villages().isEmpty()) {
            applied.add("villages");
        }
        return applied;
    }

    /// Asks the profession's predicate, not names, since another mod need not name its job site after it.
    public static Optional<RegistryEntry<PointOfInterestType>> jobSiteOf(VillagerProfession profession) {
        return Registries.POINT_OF_INTEREST_TYPE.streamEntries()
                .filter(entry -> profession.acquirableWorkstation().test(entry))
                .map(entry -> (RegistryEntry<PointOfInterestType>) entry)
                .findFirst();
    }

    private static void addWorkstations(ProfessionDefinition definition,
                                        RegistryEntry<PointOfInterestType> jobSite) {
        List<Identifier> missing = new ArrayList<>();
        List<String> taken = new ArrayList<>();
        List<String> unsuitable = new ArrayList<>();
        int added = 0;

        for (Identifier blockId : definition.addWorkstations()) {
            Optional<Block> block = Registries.BLOCK.getOptionalValue(blockId);
            if (block.isEmpty()) {
                missing.add(blockId);
                continue;
            }

            BlockVerdict verdict = check(block.get(), definition.allowNaturalBlock(), null, Map.of());
            if (verdict.unsuitableReason().isPresent()) {
                unsuitable.add(verdict.unsuitableReason().get());
                continue;
            }
            if (verdict.ownerId().isPresent()) {
                taken.add(blockId + " (already " + verdict.ownerId().get() + ")");
                continue;
            }
            for (BlockState state : PointOfInterestTypesAccessor.ddv$getStatesOfBlock(block.get())) {
                PointOfInterestTypesAccessor.ddv$poiStatesToType().put(state, jobSite);
            }
            added++;
        }

        if (!missing.isEmpty()) {
            DataDrivenVillagers.LOGGER.warn("Override {} ignores unknown block(s): {}",
                    definition.target(), missing);
        }
        if (!taken.isEmpty()) {
            DataDrivenVillagers.LOGGER.warn("Override {} ignores block(s) that already are a job site: {}",
                    definition.target(), taken);
        }
        if (!unsuitable.isEmpty()) {
            DataDrivenVillagers.LOGGER.warn("Override {} ignores unsuitable block(s): {}",
                    definition.target(), unsuitable);
        }
        if (added > 0) {
            DataDrivenVillagers.LOGGER.info("Gave {} block(s) to the existing job site of {}",
                    added, definition.target());
        }
    }

    private static void parseOne(Path file, List<ProfessionDefinition> target) {
        String fileName = file.getFileName().toString();
        String base = fileName.substring(0, fileName.length() - EXTENSION.length());
        try {
            JsonObject root;
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            }
            target.add(ProfessionParser.parse(base, root));
        } catch (Exception e) {
            reject(fileName, e);
        }
    }

    /// Without a texture vanilla derives an id from the profession id and shows the missing texture.
    private static void warnIfTextureless(ProfessionDefinition definition) {
        // An override keeps the texture of the profession it modifies.
        if (definition.isOverride()) {
            return;
        }
        if (definition.texture().isEmpty() && definition.textureFile().isEmpty()) {
            DataDrivenVillagers.LOGGER.warn("Profession {} defines no \"texture\", its villagers will "
                    + "render with the missing texture. Put a png next to the json and name it there.",
                    definition.id());
        }
    }

    /// Records the reason for `/ddv errors`; the game keeps going without this profession.
    private static void reject(String fileName, Exception e) {
        String safeName = DefinitionParseException.sanitize(fileName);
        String reason = DefinitionParseException.readableReason(e);
        ProfessionRegistry.addError(safeName, reason);
        if (e instanceof DefinitionParseException || e instanceof JsonParseException) {
            DataDrivenVillagers.LOGGER.error("Skipping profession file {}: {}", safeName, reason);
        } else {
            DataDrivenVillagers.LOGGER.error("Skipping profession file {}: {}", safeName, reason, e);
        }
    }

    private static PointOfInterestType createPointOfInterest(ProfessionDefinition definition,
                                                               Map<BlockState, Identifier> claimedByEarlierFiles) {
        Set<BlockState> states = new LinkedHashSet<>();
        List<Identifier> missing = new ArrayList<>();
        List<String> taken = new ArrayList<>();
        List<String> unsuitable = new ArrayList<>();

        for (Identifier blockId : definition.workstations()) {
            Optional<Block> block = Registries.BLOCK.getOptionalValue(blockId);
            if (block.isEmpty()) {
                missing.add(blockId);
                continue;
            }

            BlockVerdict verdict = check(block.get(), definition.allowNaturalBlock(), null, claimedByEarlierFiles);
            if (verdict.unsuitableReason().isPresent()) {
                unsuitable.add(verdict.unsuitableReason().get());
                continue;
            }
            if (verdict.ownerId().isPresent()) {
                taken.add(blockId + " (already " + verdict.ownerId().get() + ")");
                continue;
            }
            states.addAll(PointOfInterestTypesAccessor.ddv$getStatesOfBlock(block.get()));
        }

        if (states.isEmpty()) {
            throw new DefinitionParseException(reasonForNoStates(definition, missing, taken, unsuitable));
        }
        if (!missing.isEmpty()) {
            // A partially resolvable list is a warning: blocks from an absent mod must not break the file.
            DataDrivenVillagers.LOGGER.warn("Profession {} ignores unknown workstation block(s): {}",
                    definition.id(), missing);
        }
        if (!taken.isEmpty()) {
            DataDrivenVillagers.LOGGER.warn("Profession {} ignores workstation block(s) that already are a job site: {}",
                    definition.id(), taken);
        }
        if (!unsuitable.isEmpty()) {
            DataDrivenVillagers.LOGGER.warn("Profession {} ignores unsuitable workstation block(s): {}",
                    definition.id(), unsuitable);
        }

        return new PointOfInterestType(Set.copyOf(states), definition.ticketCount(), definition.searchDistance());
    }

    static Optional<String> unsuitableWorkstation(Block block, boolean allowNaturalBlock) {
        Identifier id = Registries.BLOCK.getId(block);
        if (block.getDefaultState().isAir()) {
            return Optional.of(id + " is air and cannot be a workstation");
        }
        if (block instanceof FluidBlock) {
            return Optional.of(id + " is a fluid and cannot be a workstation");
        }
        if (block.getDefaultState().getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN).isEmpty()) {
            return Optional.of(id + " has no collision and cannot be a workstation");
        }
        if (NaturalBlocks.IDS.contains(id)) {
            if (!allowNaturalBlock) {
                return Optional.of(id + " generates naturally in large numbers; every one in the world "
                        + "would become a job site. Set \"allow_natural_block\": true to use it anyway");
            }
            if (LOGGED_ALLOWED_ANYWAY.add(id)) {
                DataDrivenVillagers.LOGGER.info("{} generates naturally in large numbers, allowed anyway by "
                        + "\"allow_natural_block\"", id);
            }
        }
        return Optional.empty();
    }

    /// Whether a block is usable as a workstation, and who owns it if not; the one check every claim,
    /// reclaim and validation path shares. `ownId` exempts a block this profession already owns.
    /// `pendingClaims` are claims made earlier in the same load pass, before they reach POI_STATES_TO_TYPE.
    public record BlockVerdict(Optional<String> unsuitableReason, Optional<String> ownerId, boolean anyStateFree) {
        public boolean usable() {
            return unsuitableReason.isEmpty() && ownerId.isEmpty();
        }
    }

    public static BlockVerdict check(Block block, boolean allowNaturalBlock, String ownId,
                                     Map<BlockState, Identifier> pendingClaims) {
        Optional<String> unsuitable = unsuitableWorkstation(block, allowNaturalBlock);
        if (unsuitable.isPresent()) {
            return new BlockVerdict(unsuitable, Optional.empty(), false);
        }

        Set<BlockState> states = PointOfInterestTypesAccessor.ddv$getStatesOfBlock(block);
        for (BlockState state : states) {
            Identifier claimed = pendingClaims.get(state);
            if (claimed != null) {
                return new BlockVerdict(Optional.empty(), Optional.of(claimed.toString()), false);
            }
        }

        boolean anyFree = false;
        for (BlockState state : states) {
            if (PointOfInterestTypesAccessor.ddv$poiStatesToType().get(state) == null) {
                anyFree = true;
                break;
            }
        }

        Optional<String> owner = existingOwner(states);
        if (owner.isPresent() && (ownId == null || !owner.get().equals(ownId))) {
            return new BlockVerdict(Optional.empty(), owner, anyFree);
        }
        return new BlockVerdict(Optional.empty(), Optional.empty(), anyFree);
    }

    public static boolean isNaturalBlock(Identifier blockId) {
        return NaturalBlocks.IDS.contains(blockId);
    }

    public static boolean isStructureBulkBlock(Identifier blockId) {
        return StructureBulkBlocks.IDS.contains(blockId);
    }

    /// One point of interest type per block state; NeoForge aborts a second claim, Fabric lets the last writer win.
    public static Optional<String> existingOwner(Set<BlockState> states) {
        for (BlockState state : states) {
            RegistryEntry<PointOfInterestType> existing = PointOfInterestTypesAccessor.ddv$poiStatesToType().get(state);
            if (existing != null) {
                return Optional.of(existing.getKey()
                        .map(key -> key.getValue().toString())
                        .orElse("another job site"));
            }
        }
        return Optional.empty();
    }

    /// {@link #createPointOfInterest}'s rejection; a block this profession already owns still counts as usable.
    public static Optional<String> workstationRejection(ProfessionDefinition definition) {
        if (definition.isOverride()) {
            return Optional.empty();
        }
        String own = definition.id().toString();
        List<Identifier> missing = new ArrayList<>();
        List<String> taken = new ArrayList<>();
        List<String> unsuitable = new ArrayList<>();
        int usable = 0;

        for (Identifier blockId : definition.workstations()) {
            Optional<Block> block = Registries.BLOCK.getOptionalValue(blockId);
            if (block.isEmpty()) {
                missing.add(blockId);
                continue;
            }
            BlockVerdict verdict = check(block.get(), definition.allowNaturalBlock(), own, Map.of());
            if (verdict.unsuitableReason().isPresent()) {
                unsuitable.add(verdict.unsuitableReason().get());
                continue;
            }
            if (verdict.ownerId().isPresent()) {
                taken.add(blockId + " (already " + verdict.ownerId().get() + ")");
                continue;
            }
            usable++;
        }

        return usable == 0
                ? Optional.of(reasonForNoStates(definition, missing, taken, unsuitable))
                : Optional.empty();
    }

    private static String reasonForNoStates(ProfessionDefinition definition, List<Identifier> missing,
                                             List<String> taken, List<String> unsuitable) {
        if (!unsuitable.isEmpty()) {
            return "no usable workstation block: " + unsuitable
                    + (missing.isEmpty() ? "" : ", unknown: " + missing)
                    + (taken.isEmpty() ? "" : ", already taken: " + taken);
        }
        if (!taken.isEmpty() && missing.isEmpty()) {
            return "workstation block(s) already belong to another job site: " + taken;
        }
        if (!taken.isEmpty()) {
            return "no usable workstation block, unknown: " + missing + ", already taken: " + taken;
        }
        return "none of the workstation blocks exist: " + definition.workstations();
    }

    /// Identity comparison: the point of interest may not be registered yet, so there is no key to match.
    private static VillagerProfession createProfession(ProfessionDefinition definition, PointOfInterestType poi) {
        Predicate<RegistryEntry<PointOfInterestType>> matches = entry -> entry.value() == poi;
        return new VillagerProfession(
                displayName(definition),
                matches,
                matches,
                items(definition),
                blocks(definition),
                workSound(definition));
    }

    private static Text displayName(ProfessionDefinition definition) {
        String key = PlatformInfo.villagerNameKey(definition.target());
        return definition.displayName()
                .map(fallback -> (Text) Text.translatableWithFallback(key, fallback))
                .orElseGet(() -> Text.translatable(key));
    }

    private static ImmutableSet<Item> items(ProfessionDefinition definition) {
        ImmutableSet.Builder<Item> builder = ImmutableSet.builder();
        for (Identifier id : definition.gatherable()) {
            Registries.ITEM.getOptionalValue(id).ifPresentOrElse(builder::add,
                    () -> DataDrivenVillagers.LOGGER.warn("Profession {} ignores unknown gatherable item {}",
                            definition.id(), id));
        }
        return builder.build();
    }

    private static ImmutableSet<Block> blocks(ProfessionDefinition definition) {
        ImmutableSet.Builder<Block> builder = ImmutableSet.builder();
        for (Identifier id : definition.secondarySites()) {
            Registries.BLOCK.getOptionalValue(id).ifPresentOrElse(builder::add,
                    () -> DataDrivenVillagers.LOGGER.warn("Profession {} ignores unknown secondary job site {}",
                            definition.id(), id));
        }
        return builder.build();
    }

    /// Null is a legal work sound; vanilla nitwits have none.
    private static SoundEvent workSound(ProfessionDefinition definition) {
        return definition.workSound()
                .map(id -> Registries.SOUND_EVENT.getOptionalValue(id).orElseGet(() -> {
                    DataDrivenVillagers.LOGGER.warn("Profession {} ignores unknown work sound {}",
                            definition.id(), id);
                    return null;
                }))
                .orElse(null);
    }
}
