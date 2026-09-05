package com.lion.datadrivenvillagers.type;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lion.datadrivenvillagers.DataDrivenVillagers;
import com.lion.datadrivenvillagers.DefinitionParseException;
import com.lion.datadrivenvillagers.ReloadOutcome;
import com.lion.datadrivenvillagers.platform.ConfigDirectory;
import com.lion.datadrivenvillagers.platform.RegistryHelper;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.village.VillagerType;
import net.minecraft.world.biome.Biome;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/// Reads every villager type file during startup, registers the type and claims its biomes in
/// `VillagerType.BIOME_TO_TYPE`. Named biomes are claimed at registration; tag members only exist once
/// a world loads, so those are claimed from the tag hook ({@link #claimTaggedBiomes}).
public final class TypeLoader {

    private static final String FOLDER = "types";
    private static final String EXTENSION = ".json";

    private static final List<TypeDefinition> PARSED = new ArrayList<>();

    /// Who held a biome before we took it, recorded on the first claim and never overwritten, so a
    /// reload can give the biome back instead of leaving it unmapped.
    private static final Map<RegistryKey<Biome>, Optional<VillagerType>> DISPLACED =
            new HashMap<>();

    private static boolean prepared;

    private TypeLoader() {
    }

    public static Path directory() {
        return ConfigDirectory.getConfigDirectory().resolve(DataDrivenVillagers.MOD_ID).resolve(FOLDER);
    }

    /// Disk to definitions, no registry access.
    public static void prepare() {
        if (prepared) {
            return;
        }
        prepared = true;

        Path dir = directory();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            DataDrivenVillagers.LOGGER.error("Could not create {}, no villager types will be loaded", dir, e);
            return;
        }

        parseInto(dir, PARSED);
    }

    private static void parseInto(Path dir, List<TypeDefinition> target) {
        List<Path> files;
        try (Stream<Path> stream = Files.list(dir)) {
            files = stream.filter(p -> p.getFileName().toString().endsWith(EXTENSION))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            DataDrivenVillagers.LOGGER.error("Could not read {}, no villager types will be loaded", dir, e);
            return;
        }

        for (Path file : files) {
            parseOne(file, target);
        }
    }

    /// Registers the types and claims the biomes named outright. Tags come later through
    /// {@link #claimTaggedBiomes}.
    public static void registerTypes() {
        prepare();
        for (TypeDefinition definition : PARSED) {
            try {
                VillagerType type = RegistryHelper.registerVillagerType(definition.id(),
                        new VillagerType(definition.name()));
                TypeRegistry.add(definition);
                claimNamedBiomes(definition, type);
            } catch (Exception e) {
                reject(definition.name() + EXTENSION, e);
            }
        }

        if (!TypeRegistry.isEmpty() || !TypeRegistry.errors().isEmpty()) {
            DataDrivenVillagers.LOGGER.info("Loaded {} villager type(s) from {}, {} file(s) rejected",
                    TypeRegistry.ordered().size(), directory(), TypeRegistry.errors().size());
        }
    }

    /// A biome named outright wins over whatever held it, vanilla included.
    private static void claimNamedBiomes(TypeDefinition definition, VillagerType type) {
        for (Identifier biome : definition.biomes()) {
            RegistryKey<Biome> key = RegistryKey.of(RegistryKeys.BIOME, biome);
            VillagerType previous = VillagerType.BIOME_TO_TYPE.put(key, type);
            remember(key, previous);
            if (previous != null && previous != type) {
                DataDrivenVillagers.LOGGER.info("Villager type {} takes biome {} from {}",
                        definition.id(), biome, idOf(previous));
            }
        }
    }

    /// Records the displaced owner once, and only when it is not one of ours: a reload must restore
    /// the original owner, not an earlier claim of our own.
    private static void remember(RegistryKey<Biome> biome, VillagerType previous) {
        if (previous != null && ours(previous)) {
            return;
        }
        DISPLACED.putIfAbsent(biome, Optional.ofNullable(previous));
    }

    /// @return null for a type that is not registered, which no definition can match
    private static Identifier idOf(VillagerType type) {
        return Registries.VILLAGER_TYPE.getId(type);
    }

    private static boolean ours(VillagerType type) {
        Identifier id = idOf(type);
        return id != null && TypeRegistry.get(id).isPresent();
    }

    /// Called per biome tag while tags are bound, again after every datapack load, so it is idempotent.
    /// A tag only fills biomes nothing holds yet; `#minecraft:is_taiga` must not repaint vanilla's own.
    public static void claimTaggedBiomes(Identifier tagId, List<Identifier> biomes) {
        for (TypeDefinition definition : TypeRegistry.withBiomeTags()) {
            if (!definition.biomeTags().contains(tagId)) {
                continue;
            }

            VillagerType type = Registries.VILLAGER_TYPE.get(definition.id());
            if (type == null) {
                continue;
            }

            int claimed = 0;
            for (Identifier biome : biomes) {
                RegistryKey<Biome> key = RegistryKey.of(RegistryKeys.BIOME, biome);
                if (VillagerType.BIOME_TO_TYPE.putIfAbsent(key, type) == null) {
                    claimed++;
                    remember(key, null);
                }
            }

            if (claimed > 0) {
                DataDrivenVillagers.LOGGER.info("Villager type {} claimed {} free biome(s) from tag #{}",
                        definition.id(), claimed, tagId);
            }
        }
    }

    /// Reads every type file again. Only the registry entry is frozen, and `VillagerType` carries no
    /// data, so texture and biomes both follow at once.
    ///
    /// @param biomes the world's biome registry, needed to resolve tag members
    public static List<ReloadOutcome> reload(Registry<Biome> biomes) {
        TypeRegistry.clearErrors();
        List<TypeDefinition> fresh = new ArrayList<>();
        parseInto(directory(), fresh);

        // Before the swap: needs the old definitions to know which biomes are ours.
        releaseBiomes();

        List<ReloadOutcome> outcomes = new ArrayList<>();
        Map<Identifier, TypeDefinition> previous = new LinkedHashMap<>();
        TypeRegistry.ordered().forEach(definition -> previous.put(definition.id(), definition));

        for (TypeDefinition next : fresh) {
            String file = next.name() + EXTENSION;
            TypeDefinition old = previous.remove(next.id());
            if (old == null) {
                outcomes.add(ReloadOutcome.restart(file,
                        "new villager type, and a type is registered before any world exists"));
                continue;
            }
            TypeRegistry.replace(next);
            outcomes.add(old.equals(next)
                    ? ReloadOutcome.unchanged(file)
                    : ReloadOutcome.updated(file, changes(old, next)));
        }

        for (TypeDefinition gone : previous.values()) {
            TypeRegistry.remove(gone.id());
            outcomes.add(ReloadOutcome.restart(gone.name() + EXTENSION,
                    "file is gone, its biomes were given back but the type itself stays"));
        }

        for (TypeDefinition definition : TypeRegistry.ordered()) {
            VillagerType type = Registries.VILLAGER_TYPE.get(definition.id());
            if (type != null) {
                claimNamedBiomes(definition, type);
            }
        }
        claimTags(biomes);

        for (TypeRegistry.LoadError error : TypeRegistry.errors()) {
            outcomes.add(ReloadOutcome.rejected(error.file(), error.reason()));
        }

        DataDrivenVillagers.newGeneration();
        return outcomes;
    }

    private static String changes(TypeDefinition old, TypeDefinition next) {
        List<String> changed = new ArrayList<>();
        if (!old.texture().equals(next.texture()) || !old.textureFile().equals(next.textureFile())) {
            changed.add("texture");
        }
        if (!old.biomes().equals(next.biomes())) {
            changed.add("biomes");
        }
        if (!old.biomeTags().equals(next.biomeTags())) {
            changed.add("biome tags");
        }
        return String.join(", ", changed);
    }

    /// Gives every biome we hold back to its previous owner. Collected first, then changed, because
    /// this walks the map it edits.
    private static void releaseBiomes() {
        List<RegistryKey<Biome>> ours = new ArrayList<>();
        for (Map.Entry<RegistryKey<Biome>, VillagerType> entry : VillagerType.BIOME_TO_TYPE.entrySet()) {
            if (ours(entry.getValue())) {
                ours.add(entry.getKey());
            }
        }

        for (RegistryKey<Biome> biome : ours) {
            DISPLACED.getOrDefault(biome, Optional.empty())
                    .ifPresentOrElse(before -> VillagerType.BIOME_TO_TYPE.put(biome, before),
                            () -> VillagerType.BIOME_TO_TYPE.remove(biome));
        }
    }

    /// Resolved once per tag; {@link #claimTaggedBiomes} hands the members to every type naming it.
    private static void claimTags(Registry<Biome> biomes) {
        Set<Identifier> tags = new LinkedHashSet<>();
        TypeRegistry.withBiomeTags().forEach(definition -> tags.addAll(definition.biomeTags()));

        for (Identifier tagId : tags) {
            biomes.getEntryList(TagKey.of(RegistryKeys.BIOME, tagId)).ifPresent(tag -> {
                List<Identifier> members = new ArrayList<>();
                for (RegistryEntry<Biome> entry : tag) {
                    entry.getKey().ifPresent(key -> members.add(key.getValue()));
                }
                claimTaggedBiomes(tagId, members);
            });
        }
    }

    private static void parseOne(Path file, List<TypeDefinition> target) {
        String fileName = file.getFileName().toString();
        String base = fileName.substring(0, fileName.length() - EXTENSION.length());
        try {
            JsonObject root;
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            }
            target.add(TypeParser.parse(base, root));
        } catch (Exception e) {
            reject(fileName, e);
        }
    }

    private static void reject(String fileName, Exception e) {
        String reason = DefinitionParseException.readableReason(e);
        TypeRegistry.addError(fileName, reason);
        DataDrivenVillagers.LOGGER.error("Skipping villager type file {}: {}", fileName, reason);
    }
}
