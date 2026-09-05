package com.lion.datadrivenvillagers.structure;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lion.datadrivenvillagers.ConfigFiles;
import com.lion.datadrivenvillagers.DataDrivenVillagers;
import com.lion.datadrivenvillagers.DefinitionParseException;
import com.lion.datadrivenvillagers.ReloadOutcome;
import com.lion.datadrivenvillagers.platform.ConfigDirectory;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.structure.pool.StructurePoolElement;
import net.minecraft.structure.processor.StructureProcessorList;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/// Appends buildings from the config folder to the village template pools at runtime. A datapack can
/// only replace a whole pool file, so two packs adding a house would overwrite each other.
///
/// Template pools are a datapack registry rebuilt per world, so unlike professions and types this runs
/// at server start and can run again.
public final class StructureLoader {

    private static final String FOLDER = "structures";
    private static final String EXTENSION = ".json";

    /// Appended elements per pool, so a reload can remove them. A pool holds one entry per point of
    /// weight; the element is stored once and every copy removed.
    private static final Map<Identifier, List<StructurePoolElement>> INJECTED = new LinkedHashMap<>();

    private StructureLoader() {
    }

    public static Path directory() {
        return ConfigDirectory.getConfigDirectory().resolve(DataDrivenVillagers.MOD_ID).resolve(FOLDER);
    }

    /// The nbt of a definition. Used by `/ddv why` and the template mixin alike. The parser holds the
    /// name to a plain file name; this is the second lock on the same door.
    public static Path fileOf(StructureDefinition definition) {
        String name = definition.file().orElseThrow(() ->
                new IllegalStateException(definition.id() + " is a generated plot and has no file"));
        return ConfigFiles.resolveInside(directory(), name).orElseThrow(() ->
                new DefinitionParseException("\"structure\" names \"" + name
                        + "\", which is not a file inside " + directory()));
    }

    /// Reads the folder and appends every piece to this world's pools. Re-runnable: a previous run's
    /// elements are removed first.
    public static List<ReloadOutcome> load(MinecraftServer server) {
        Map<Identifier, StructureDefinition> before = new LinkedHashMap<>();
        StructureRegistry.ordered().forEach(definition -> before.put(definition.id(), definition));

        StructureRegistry.clearErrors();
        List<StructureDefinition> fresh = new ArrayList<>();
        parseInto(fresh);

        Registry<StructurePool> pools = server.getRegistryManager().getOrThrow(RegistryKeys.TEMPLATE_POOL);
        takeBack(pools);

        Set<Identifier> present = new LinkedHashSet<>();
        for (StructureDefinition definition : fresh) {
            StructureRegistry.add(definition);
            present.add(definition.id());
        }
        for (StructureDefinition old : StructureRegistry.ordered()) {
            if (!present.contains(old.id())) {
                StructureRegistry.remove(old.id());
            }
        }

        Registry<StructureProcessorList> processors =
                server.getRegistryManager().getOrThrow(RegistryKeys.PROCESSOR_LIST);
        int wired = 0;
        for (StructureDefinition definition : StructureRegistry.ordered()) {
            wired += inject(definition, pools, processors);
        }

        if (!StructureRegistry.isEmpty() || !StructureRegistry.errors().isEmpty()) {
            DataDrivenVillagers.LOGGER.info("Added {} structure(s) from {} to {} pool(s), {} file(s) rejected",
                    StructureRegistry.ordered().size(), directory(), wired, StructureRegistry.errors().size());
        }

        DataDrivenVillagers.newGeneration();
        return outcomes(before);
    }

    /// Structures reload whole: the nbt is read on demand and the pools are rebuilt per world, so no
    /// outcome ever needs a restart.
    private static List<ReloadOutcome> outcomes(Map<Identifier, StructureDefinition> before) {
        List<ReloadOutcome> outcomes = new ArrayList<>();
        for (StructureDefinition definition : StructureRegistry.ordered()) {
            String file = definition.name() + EXTENSION;
            StructureDefinition old = before.remove(definition.id());
            if (old == null) {
                outcomes.add(ReloadOutcome.updated(file, "added to "
                        + definition.targetPools().size() + " pool(s)"));
            } else {
                outcomes.add(old.equals(definition)
                        ? ReloadOutcome.unchanged(file)
                        : ReloadOutcome.updated(file, "pools rebuilt"));
            }
        }
        for (StructureDefinition gone : before.values()) {
            outcomes.add(ReloadOutcome.updated(gone.name() + EXTENSION, "file is gone, taken back out"));
        }
        for (StructureRegistry.LoadError error : StructureRegistry.errors()) {
            outcomes.add(ReloadOutcome.rejected(error.file(), error.reason()));
        }
        return outcomes;
    }

    /// Appends to `StructurePool.elements` only. Vanilla reads that expanded list everywhere
    /// (`getRandomElement`, `getElementIndicesInRandomOrder`, `getElementCount`, `getHighestY`);
    /// the immutable `elementWeights` is used by the codec alone.
    private static int inject(StructureDefinition definition, Registry<StructurePool> pools,
                              Registry<StructureProcessorList> processors) {
        List<Identifier> missing = new ArrayList<>();
        int wired = 0;
        for (Identifier poolId : definition.targetPools()) {
            StructurePool pool = pools.getOptionalValue(poolId).orElse(null);
            if (pool == null) {
                missing.add(poolId);
                continue;
            }
            // Per pool: a generated plot has a different template id per village type.
            StructurePoolElement element = element(definition, definition.templateId(poolId), processors);
            if (element == null) {
                return 0;
            }
            for (int i = 0; i < definition.weight(); i++) {
                pool.elements.add(element);
            }
            INJECTED.computeIfAbsent(poolId, key -> new ArrayList<>()).add(element);
            wired++;
        }

        if (!missing.isEmpty()) {
            // A missing pool is a warning as long as another one was found; a datapack may have removed it.
            String reason = "no such template pool: " + missing;
            if (wired == 0) {
                StructureRegistry.addError(definition.name() + EXTENSION, reason);
            } else {
                DataDrivenVillagers.LOGGER.warn("Structure {} skips {}", definition.id(), reason);
            }
        }
        return wired;
    }

    /// @return null when the named processor list does not exist in this world; the error is recorded
    private static StructurePoolElement element(StructureDefinition definition, Identifier templateId,
                                                Registry<StructureProcessorList> processors) {
        // Legacy single elements, like vanilla's houses: they ignore air, the modern element places it
        // and carves a box out of the ground.
        StructurePool.Projection projection = definition.ground() == GroundKind.RIGID
                ? StructurePool.Projection.RIGID
                : StructurePool.Projection.TERRAIN_MATCHING;
        String location = templateId.toString();

        if (definition.processors().isEmpty()) {
            return StructurePoolElement.ofLegacySingle(location).apply(projection);
        }

        Identifier id = definition.processors().get();
        Optional<RegistryEntry.Reference<StructureProcessorList>> entry = processors.getEntry(id);
        if (entry.isEmpty()) {
            StructureRegistry.addError(definition.name() + EXTENSION,
                    "\"processors\" names " + id + ", which is not a processor list in this world");
            return null;
        }
        return StructurePoolElement.ofProcessedLegacySingle(location, entry.get()).apply(projection);
    }

    private static void takeBack(Registry<StructurePool> pools) {
        for (Map.Entry<Identifier, List<StructurePoolElement>> entry : INJECTED.entrySet()) {
            pools.getOptionalValue(entry.getKey()).ifPresent(pool ->
                    pool.elements.removeIf(element -> entry.getValue().contains(element)));
        }
        INJECTED.clear();
    }

    private static void parseInto(List<StructureDefinition> target) {
        Path dir = directory();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            DataDrivenVillagers.LOGGER.error("Could not create {}, no structures will be loaded", dir, e);
            return;
        }

        List<Path> files;
        try (Stream<Path> stream = Files.list(dir)) {
            files = stream.filter(p -> p.getFileName().toString().endsWith(EXTENSION))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            DataDrivenVillagers.LOGGER.error("Could not read {}, no structures will be loaded", dir, e);
            return;
        }

        for (Path file : files) {
            parseOne(file, target);
        }
    }

    private static void parseOne(Path file, List<StructureDefinition> target) {
        String fileName = file.getFileName().toString();
        String base = fileName.substring(0, fileName.length() - EXTENSION.length());
        try {
            JsonObject root;
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            }
            StructureDefinition definition = StructureParser.parse(base, root);

            // Checked at load: a missing file at generation time would leave a hole in a village.
            if (!definition.generated() && !Files.isRegularFile(fileOf(definition))) {
                throw new DefinitionParseException("\"structure\" names " + definition.file().get()
                        + ", which is not a file in " + directory());
            }
            // The template reader silently turns an unknown block into air; the block registry is
            // complete by now, so it is checked here.
            if (definition.generated() && !Registries.BLOCK.containsId(definition.workstation().get())) {
                throw new DefinitionParseException("\"workstation\" names " + definition.workstation().get()
                        + ", which is not a block in this game");
            }
            target.add(definition);
        } catch (Exception e) {
            String reason = DefinitionParseException.readableReason(e);
            StructureRegistry.addError(fileName, reason);
            DataDrivenVillagers.LOGGER.error("Skipping structure file {}: {}", fileName, reason);
        }
    }
}
