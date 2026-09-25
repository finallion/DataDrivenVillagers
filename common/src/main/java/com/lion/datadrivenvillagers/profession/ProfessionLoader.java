package com.lion.datadrivenvillagers.profession;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lion.datadrivenvillagers.DataDrivenVillagers;
import com.lion.datadrivenvillagers.DefinitionParseException;
import com.lion.datadrivenvillagers.ReloadOutcome;
import com.lion.datadrivenvillagers.platform.ConfigDirectory;
import com.lion.datadrivenvillagers.platform.RegistryHelper;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.poi.PointOfInterestType;
import net.minecraft.world.poi.PointOfInterestTypes;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/// Reads every profession file during startup and registers a point of interest plus a villager
/// profession for each. Three phases, because NeoForge hands out one RegisterEvent per registry and
/// the block registry is only complete when the point of interest registry comes up. A broken file is
/// logged and skipped, never stops the others.
public final class ProfessionLoader {

    private static final String FOLDER = "professions";
    private static final String EXTENSION = ".json";

    private static final List<ProfessionDefinition> PARSED = new ArrayList<>();
    private static final Map<Identifier, PointOfInterestType> POINTS_OF_INTEREST = new LinkedHashMap<>();

    private ProfessionLoader() {
    }

    public static Path directory() {
        return ConfigDirectory.getConfigDirectory().resolve(DataDrivenVillagers.MOD_ID).resolve(FOLDER);
    }

    private static boolean prepared;

    /// Fabric registers everything in one init, so the phases run back to back.
    public static void loadAll() {
        registerProfessions();
        registerPointsOfInterest();
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

    // Phase 1: disk to definitions. No registry access, so a reload can run it again into its own list.
    private static void parseInto(List<ProfessionDefinition> target) {
        Path dir = directory();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            DataDrivenVillagers.LOGGER.error("Could not create {}, no professions will be loaded", dir, e);
            return;
        }

        ExampleProfession.writeIfFolderIsEmpty(dir);

        List<Path> files;
        try (Stream<Path> stream = Files.list(dir)) {
            files = stream.filter(p -> p.getFileName().toString().endsWith(EXTENSION))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            DataDrivenVillagers.LOGGER.error("Could not read {}, no professions will be loaded", dir, e);
            return;
        }

        for (Path file : files) {
            parseOne(file, target);
        }
    }

    // Phase 2: needs a complete block registry; drops definitions with a missing or claimed workstation.
    private static void buildPointsOfInterest() {
        Iterator<ProfessionDefinition> iterator = PARSED.iterator();
        while (iterator.hasNext()) {
            ProfessionDefinition definition = iterator.next();
            if (definition.isOverride()) {
                // Uses an existing job site, nothing to build.
                continue;
            }
            try {
                POINTS_OF_INTEREST.put(definition.id(), createPointOfInterest(definition));
            } catch (Exception e) {
                reject(definition.name() + EXTENSION, e);
                iterator.remove();
            }
        }
    }

    // Phase 3a. Needs only the instance, not its registration: the predicate compares by identity.
    public static void registerProfessions() {
        prepare();
        for (ProfessionDefinition definition : PARSED) {
            if (definition.isOverride()) {
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

    // Phase 3b. Registers the point of interest, then fills POI_STATES_TO_TYPE, which needs the registry entry.
    public static void registerPointsOfInterest() {
        prepare();
        for (ProfessionDefinition definition : PARSED) {
            if (definition.isOverride()) {
                try {
                    applyOverride(definition);
                } catch (Exception e) {
                    reject(definition.name() + EXTENSION, e);
                }
                continue;
            }

            PointOfInterestType poi = POINTS_OF_INTEREST.get(definition.id());
            if (poi == null) {
                continue;
            }
            try {
                RegistryHelper.registerPointOfInterestType(definition.id(), poi);
                RegistryEntry<PointOfInterestType> entry = Registries.POINT_OF_INTEREST_TYPE.getEntry(poi);

                // Vanilla fills this map in static init before mod POIs exist; the sensor reads it, not the registry.
                for (BlockState state : poi.blockStates()) {
                    PointOfInterestTypes.POI_STATES_TO_TYPE.put(state, entry);
                }
                ProfessionRegistry.add(definition, entry);
                warnIfTextureless(definition);
            } catch (Exception e) {
                reject(definition.name() + EXTENSION, e);
            }
        }

        DataDrivenVillagers.LOGGER.info("Loaded {} villager profession(s) from {}, {} file(s) rejected",
                ProfessionRegistry.definitions().size(), directory(), ProfessionRegistry.errors().size());
    }

    /// Rereads every file; fields baked into vanilla's frozen record need a restart to apply.
    public static List<ReloadOutcome> reload(MinecraftServer server) {
        ProfessionRegistry.clearErrors();
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

        for (ProfessionRegistry.LoadError error : ProfessionRegistry.errors()) {
            outcomes.add(ReloadOutcome.rejected(error.file(), rejectionDetail(error, kept)));
        }

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
            Map<Identifier, ProfessionDefinition> previous, List<ProfessionRegistry.LoadError> errors) {
        Set<String> rejectedFiles = new LinkedHashSet<>();
        for (ProfessionRegistry.LoadError error : errors) {
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
    static String rejectionDetail(ProfessionRegistry.LoadError error, Map<Identifier, ProfessionDefinition> kept) {
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
            createPointOfInterest(definition);
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
            return true;
        } catch (Exception e) {
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
        List<String> ignored = ignoredFields(definition);
        if (!ignored.isEmpty()) {
            said.add("an override never reads " + String.join(", ", ignored));
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
        return Registries.VILLAGER_PROFESSION.getId(villager.getVillagerData().getProfession());
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

        List<String> ignored = ignoredFields(next);
        if (!ignored.isEmpty()) {
            applied.add("an override never reads " + String.join(", ", ignored));
        }

        List<String> frozen = frozenFields(old, next);
        if (frozen.isEmpty()) {
            return ReloadOutcome.updated(file, String.join(", ", applied));
        }
        return ReloadOutcome.restart(file, (applied.isEmpty() ? "" : String.join(", ", applied) + "; ")
                + "restart for " + String.join(", ", frozen));
    }

    /// `blockStates()` is frozen once registered; the sensor reads the mutable POI_STATES_TO_TYPE map.
    private static String moveWorkstations(ProfessionDefinition old, ProfessionDefinition next) {
        List<Identifier> before = old.isOverride() ? old.addWorkstations() : old.workstations();
        List<Identifier> after = next.isOverride() ? next.addWorkstations() : next.workstations();
        if (before.equals(after)) {
            return "";
        }

        RegistryEntry<PointOfInterestType> poi = jobSiteFor(next).orElse(null);
        if (poi == null) {
            return "blocks unchanged, there is no job site to move them to";
        }

        int released = 0;
        int claimed = 0;
        List<Identifier> refused = new ArrayList<>();
        for (Identifier blockId : before) {
            if (!after.contains(blockId)) {
                released += releaseBlock(blockId, poi);
            }
        }
        for (Identifier blockId : after) {
            if (before.contains(blockId)) {
                continue;
            }
            if (claimBlock(blockId, poi)) {
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
            said.add("refused " + refused + ", unknown or already a job site");
        }
        return String.join(", ", said);
    }

    /// Clears only states that still point at this job site.
    private static int releaseBlock(Identifier blockId, RegistryEntry<PointOfInterestType> poi) {
        Optional<Block> block = Registries.BLOCK.getOrEmpty(blockId);
        if (block.isEmpty()) {
            return 0;
        }
        boolean removed = false;
        for (BlockState state : PointOfInterestTypes.getStatesOfBlock(block.get())) {
            if (PointOfInterestTypes.POI_STATES_TO_TYPE.get(state) == poi) {
                PointOfInterestTypes.POI_STATES_TO_TYPE.remove(state);
                removed = true;
            }
        }
        return removed ? 1 : 0;
    }

    private static boolean claimBlock(Identifier blockId, RegistryEntry<PointOfInterestType> poi) {
        Optional<Block> block = Registries.BLOCK.getOrEmpty(blockId);
        if (block.isEmpty()) {
            return false;
        }
        Set<BlockState> states = PointOfInterestTypes.getStatesOfBlock(block.get());
        if (existingOwner(states).isPresent()) {
            return false;
        }
        for (BlockState state : states) {
            PointOfInterestTypes.POI_STATES_TO_TYPE.put(state, poi);
        }
        return true;
    }

    private static void releaseWorkstations(ProfessionDefinition gone) {
        RegistryEntry<PointOfInterestType> poi = jobSiteFor(gone).orElse(null);
        if (poi == null) {
            return;
        }
        for (Identifier blockId : gone.isOverride() ? gone.addWorkstations() : gone.workstations()) {
            releaseBlock(blockId, poi);
        }
    }

    private static Optional<RegistryEntry<PointOfInterestType>> jobSiteFor(ProfessionDefinition definition) {
        if (!definition.isOverride()) {
            return ProfessionRegistry.poiEntry(definition.target());
        }
        return Registries.VILLAGER_PROFESSION.getOrEmpty(definition.target())
                .flatMap(ProfessionLoader::jobSiteOf);
    }

    /// Changed fields that live in a frozen vanilla record. Empty for an override, which never reads them.
    private static List<String> frozenFields(ProfessionDefinition old, ProfessionDefinition next) {
        if (next.isOverride()) {
            return List.of();
        }

        List<String> changed = new ArrayList<>();
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
        VillagerProfession profession = Registries.VILLAGER_PROFESSION.getOrEmpty(target)
                .orElseThrow(() -> new DefinitionParseException(
                        "\"overrides\" names " + target + ", which is not a registered profession"));

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
        warnIfIgnored(definition);
    }

    /// Fields only read while creating a profession or job site, which an override never does.
    public static List<String> ignoredFields(ProfessionDefinition definition) {
        if (!definition.isOverride()) {
            return List.of();
        }

        List<String> ignored = new ArrayList<>();
        if (definition.displayName().isPresent()) {
            ignored.add("display_name");
        }
        if (definition.workSound().isPresent()) {
            ignored.add("work_sound");
        }
        if (!definition.gatherable().isEmpty()) {
            ignored.add("gatherable_items");
        }
        if (!definition.secondarySites().isEmpty()) {
            ignored.add("secondary_job_sites");
        }
        if (definition.ticketCount() != ProfessionParser.DEFAULT_TICKET_COUNT) {
            ignored.add("ticket_count");
        }
        if (definition.searchDistance() != ProfessionParser.DEFAULT_SEARCH_DISTANCE) {
            ignored.add("search_distance");
        }
        return ignored;
    }

    /// Fields an override reads, asked per villager; with {@link #ignoredFields} every field is in exactly one list.
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

    /// Warned after registration, not while parsing, so a rejected file gets no warning on top.
    private static void warnIfIgnored(ProfessionDefinition definition) {
        List<String> ignored = ignoredFields(definition);
        if (!ignored.isEmpty()) {
            DataDrivenVillagers.LOGGER.warn("Override {} sets {}, which an override never reads: those "
                            + "belong to the profession being created, and this file modifies one that exists",
                    definition.target(), ignored);
        }
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
        int added = 0;

        for (Identifier blockId : definition.addWorkstations()) {
            Optional<Block> block = Registries.BLOCK.getOrEmpty(blockId);
            if (block.isEmpty()) {
                missing.add(blockId);
                continue;
            }

            Set<BlockState> states = PointOfInterestTypes.getStatesOfBlock(block.get());
            Optional<String> owner = existingOwner(states);
            if (owner.isPresent()) {
                taken.add(blockId + " (already " + owner.get() + ")");
                continue;
            }
            for (BlockState state : states) {
                PointOfInterestTypes.POI_STATES_TO_TYPE.put(state, jobSite);
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
        String reason = DefinitionParseException.readableReason(e);
        ProfessionRegistry.addError(fileName, reason);
        DataDrivenVillagers.LOGGER.error("Skipping profession file {}: {}", fileName, reason);
    }

    private static PointOfInterestType createPointOfInterest(ProfessionDefinition definition) {
        Set<BlockState> states = new LinkedHashSet<>();
        List<Identifier> missing = new ArrayList<>();
        List<String> taken = new ArrayList<>();

        for (Identifier blockId : definition.workstations()) {
            Optional<Block> block = Registries.BLOCK.getOrEmpty(blockId);
            if (block.isEmpty()) {
                missing.add(blockId);
                continue;
            }

            Set<BlockState> blockStates = PointOfInterestTypes.getStatesOfBlock(block.get());
            Optional<String> owner = existingOwner(blockStates);
            if (owner.isPresent()) {
                taken.add(blockId + " (already " + owner.get() + ")");
                continue;
            }
            states.addAll(blockStates);
        }

        if (states.isEmpty()) {
            throw new DefinitionParseException(reasonForNoStates(definition, missing, taken));
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

        return new PointOfInterestType(Set.copyOf(states), definition.ticketCount(), definition.searchDistance());
    }

    /// One point of interest type per block state; NeoForge aborts a second claim, Fabric lets the last writer win.
    public static Optional<String> existingOwner(Set<BlockState> states) {
        for (BlockState state : states) {
            RegistryEntry<PointOfInterestType> existing = PointOfInterestTypes.POI_STATES_TO_TYPE.get(state);
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
        int usable = 0;

        for (Identifier blockId : definition.workstations()) {
            Optional<Block> block = Registries.BLOCK.getOrEmpty(blockId);
            if (block.isEmpty()) {
                missing.add(blockId);
                continue;
            }
            Optional<String> owner = existingOwner(PointOfInterestTypes.getStatesOfBlock(block.get()));
            if (owner.isPresent() && !owner.get().equals(own)) {
                taken.add(blockId + " (already " + owner.get() + ")");
                continue;
            }
            usable++;
        }

        return usable == 0
                ? Optional.of(reasonForNoStates(definition, missing, taken))
                : Optional.empty();
    }

    private static String reasonForNoStates(ProfessionDefinition definition, List<Identifier> missing, List<String> taken) {
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
                definition.name(),
                matches,
                matches,
                items(definition),
                blocks(definition),
                workSound(definition));
    }

    /// Vanilla builds the key as `entity.minecraft.villager.<path>` regardless of namespace.
    public static Optional<Text> displayName(Identifier profession) {
        return ProfessionRegistry.get(profession)
                .flatMap(ProfessionDefinition::displayName)
                .map(fallback -> Text.translatableWithFallback(
                        "entity.minecraft.villager." + profession.getPath(), fallback));
    }

    private static ImmutableSet<Item> items(ProfessionDefinition definition) {
        ImmutableSet.Builder<Item> builder = ImmutableSet.builder();
        for (Identifier id : definition.gatherable()) {
            Registries.ITEM.getOrEmpty(id).ifPresentOrElse(builder::add,
                    () -> DataDrivenVillagers.LOGGER.warn("Profession {} ignores unknown gatherable item {}",
                            definition.id(), id));
        }
        return builder.build();
    }

    private static ImmutableSet<Block> blocks(ProfessionDefinition definition) {
        ImmutableSet.Builder<Block> builder = ImmutableSet.builder();
        for (Identifier id : definition.secondarySites()) {
            Registries.BLOCK.getOrEmpty(id).ifPresentOrElse(builder::add,
                    () -> DataDrivenVillagers.LOGGER.warn("Profession {} ignores unknown secondary job site {}",
                            definition.id(), id));
        }
        return builder.build();
    }

    /// Null is a legal work sound; vanilla nitwits have none.
    private static SoundEvent workSound(ProfessionDefinition definition) {
        return definition.workSound()
                .map(id -> Registries.SOUND_EVENT.getOrEmpty(id).orElseGet(() -> {
                    DataDrivenVillagers.LOGGER.warn("Profession {} ignores unknown work sound {}",
                            definition.id(), id);
                    return null;
                }))
                .orElse(null);
    }
}
