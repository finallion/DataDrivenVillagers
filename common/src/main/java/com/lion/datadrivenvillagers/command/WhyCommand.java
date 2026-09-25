package com.lion.datadrivenvillagers.command;

import com.lion.datadrivenvillagers.ConfigFiles;
import com.lion.datadrivenvillagers.DataDrivenVillagers;
import com.lion.datadrivenvillagers.PngHeader;
import com.lion.datadrivenvillagers.network.LookSync;
import com.lion.datadrivenvillagers.profession.EntityRange;
import com.lion.datadrivenvillagers.profession.HatKind;
import com.lion.datadrivenvillagers.profession.ProfessionDefinition;
import com.lion.datadrivenvillagers.profession.ProfessionLoader;
import com.lion.datadrivenvillagers.profession.ProfessionParser;
import com.lion.datadrivenvillagers.profession.ProfessionRegistry;
import com.lion.datadrivenvillagers.profession.ScheduleActivity;
import com.lion.datadrivenvillagers.profession.ScheduleDefinition;
import com.lion.datadrivenvillagers.profession.WorkBehaviour;
import com.lion.datadrivenvillagers.structure.StructureDefinition;
import com.lion.datadrivenvillagers.structure.StructureParser;
import com.lion.datadrivenvillagers.structure.StructureRegistry;
import com.lion.datadrivenvillagers.type.TypeDefinition;
import com.lion.datadrivenvillagers.type.TypeLoader;
import com.lion.datadrivenvillagers.type.TypeRegistry;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.loot.LootTable;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.PointOfInterestTypeTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.pool.SinglePoolElement;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.structure.pool.StructurePoolElement;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.VillagerType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.poi.PointOfInterest;
import net.minecraft.world.poi.PointOfInterestStorage;
import net.minecraft.world.poi.PointOfInterestType;
import net.minecraft.world.poi.PointOfInterestTypes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/// `/ddv why <name>`: walks the chain behind a profession, type or structure and names the link that
/// broke. Every check reads the runtime state vanilla reads (registries, POI_STATES_TO_TYPE, tags,
/// pools), never the loader's own records, because another mod can undo any of them after startup.
public final class WhyCommand {

    private static final SuggestionProvider<ServerCommandSource> KNOWN_NAMES =
            (context, builder) -> CommandSource.suggestIdentifiers(knownNames(), builder);

    private WhyCommand() {
    }

    public static LiteralArgumentBuilder<ServerCommandSource> node() {
        return CommandManager.literal("why")
                // Before the name argument, or "villager" would parse as an id.
                .then(VillagerWhy.node())
                .then(CommandManager.argument("name", IdentifierArgumentType.identifier())
                        .suggests(KNOWN_NAMES)
                        .executes(Framed.framed(WhyCommand::why)));
    }

    /// Loaded ids plus the file names of rejected files.
    private static List<Identifier> knownNames() {
        List<Identifier> names = new ArrayList<>(ProfessionRegistry.definitions().keySet());
        // Overrides are keyed by the vanilla id they change; their own file name is offered as well.
        ProfessionRegistry.ordered().stream().filter(ProfessionDefinition::isOverride)
                .forEach(definition -> names.add(definition.id()));
        TypeRegistry.ordered().forEach(definition -> names.add(definition.id()));
        StructureRegistry.ordered().forEach(definition -> names.add(definition.id()));
        ProfessionRegistry.errors().forEach(error -> rejectedName(error.file()).ifPresent(names::add));
        TypeRegistry.errors().forEach(error -> rejectedName(error.file()).ifPresent(names::add));
        StructureRegistry.errors().forEach(error -> rejectedName(error.file()).ifPresent(names::add));
        return names;
    }

    /// Empty when the file name is not a legal id path.
    private static Optional<Identifier> rejectedName(String file) {
        String base = file.endsWith(".json") ? file.substring(0, file.length() - ".json".length()) : file;
        return Optional.ofNullable(Identifier.tryParse(DataDrivenVillagers.MOD_ID + ":" + base));
    }

    private static int why(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        Identifier asked = IdentifierArgumentType.getIdentifier(context, "name");

        // A bare word arrives as minecraft:<word>; tried as typed, our namespace, then as file name.
        Optional<ProfessionDefinition> profession = ProfessionRegistry.get(asked)
                .or(() -> ProfessionRegistry.get(ours(asked)))
                .or(() -> ScaffoldCommand.find(asked.getPath()));
        if (profession.isPresent()) {
            return professionReport(source, profession.get()).send(source);
        }

        Optional<TypeDefinition> type = TypeRegistry.get(asked).or(() -> TypeRegistry.get(ours(asked)));
        if (type.isPresent()) {
            return typeReport(source, type.get()).send(source);
        }

        Optional<StructureDefinition> structure =
                StructureRegistry.get(asked).or(() -> StructureRegistry.get(ours(asked)));
        if (structure.isPresent()) {
            return structureReport(source, structure.get()).send(source);
        }

        return unknown(source, asked);
    }

    private static Identifier ours(Identifier asked) {
        return DataDrivenVillagers.id(asked.getPath());
    }

    // ---------------------------------------------------------------- professions

    /// Chain: profession registered, accepts a job site, job site in `acquirable_job_site`, a block leads back to it.
    static Report professionReport(ServerCommandSource source, ProfessionDefinition definition) {
        Report report = new Report();
        Identifier target = definition.target();
        report.header(target, (definition.isOverride() ? "override" : "profession")
                + ", from " + definition.name() + ".json");
        report.ok("file loaded", definition.name() + ".json");

        Optional<VillagerProfession> registered = Registries.VILLAGER_PROFESSION.getOrEmpty(target);
        if (registered.isEmpty()) {
            report.broken("profession registered", target + " is not in the registry",
                    definition.isOverride()
                            ? "\"overrides\" names a profession that does not exist here. Check the id, "
                                    + "or install the mod that owns it."
                            : "registration failed after the file was read, /ddv errors has the reason.");
            report.skipped("job site accepted by it", "");
            report.skipped("job site is in acquirable_job_site", "");
            report.skipped("blocks lead to the job site", "");
            return finish(source, report, definition, null);
        }
        report.ok("profession registered", target.toString());

        Optional<RegistryEntry<PointOfInterestType>> jobSite = ProfessionLoader.jobSiteOf(registered.get());
        if (jobSite.isEmpty()) {
            // An override of a profession without a job site (nitwit) is legitimate.
            if (definition.isOverride()) {
                report.skipped("job site accepted by it", target + " has no job site of its own");
            } else {
                report.broken("job site accepted by it", "the profession accepts no point of interest",
                        "the job site was not registered, /ddv errors has the reason. Fixing it needs a "
                                + "restart: job sites are registered before any world exists.");
            }
            report.skipped("job site is in acquirable_job_site", "");
            report.skipped("blocks lead to the job site", "");
            return finish(source, report, definition, null);
        }

        RegistryEntry<PointOfInterestType> poi = jobSite.get();
        report.ok("job site accepted by it", poi.getIdAsString() + blocksOf(poi));

        if (poi.isIn(PointOfInterestTypeTags.ACQUIRABLE_JOB_SITE)) {
            report.ok("job site is in acquirable_job_site", "");
        } else {
            report.broken("job site is in acquirable_job_site", poi.getIdAsString() + " is not in the tag",
                    "without the tag the job site sensor never looks at the block, however correct "
                            + "everything else is. The tag is filled while a world loads.");
        }

        blocks(report, definition, poi);
        return finish(source, report, definition, poi);
    }

    /// On Fabric, a later-registered point of interest silently takes over a block state in POI_STATES_TO_TYPE.
    private static void blocks(Report report, ProfessionDefinition definition,
                               RegistryEntry<PointOfInterestType> poi) {
        List<Identifier> declared = definition.isOverride()
                ? definition.addWorkstations()
                : definition.workstations();

        if (declared.isEmpty()) {
            report.skipped("blocks lead to the job site", "this file adds no blocks of its own");
            return;
        }

        List<Note> notes = new ArrayList<>();
        int leading = 0;

        for (Identifier blockId : declared) {
            Optional<Block> block = Registries.BLOCK.getOrEmpty(blockId);
            if (block.isEmpty()) {
                notes.add(new Note(false, blockId + "  no such block"));
                continue;
            }

            String owner = ownerOf(block.get(), poi);
            if (owner == null) {
                leading++;
                notes.add(new Note(true, blockId + "  "
                        + PointOfInterestTypes.getStatesOfBlock(block.get()).size() + " state(s)"));
            } else {
                notes.add(new Note(false, blockId + "  " + owner));
            }
        }

        if (leading == 0) {
            report.broken("blocks lead to the job site", "none of " + declared.size() + " block(s)",
                    "a villager finds the job through the block, so nothing can happen until one of "
                            + "these leads to " + poi.getIdAsString() + ".");
        } else {
            report.ok("blocks lead to the job site", leading + " of " + declared.size() + " block(s)");
        }
        report.notes(notes);
    }

    /// @return null when every state of the block leads to this job site, otherwise who holds it
    private static String ownerOf(Block block, RegistryEntry<PointOfInterestType> poi) {
        Set<BlockState> states = PointOfInterestTypes.getStatesOfBlock(block);
        if (states.isEmpty()) {
            return "block has no states";
        }

        for (BlockState state : states) {
            RegistryEntry<PointOfInterestType> holder = PointOfInterestTypes.POI_STATES_TO_TYPE.get(state);
            if (holder == null) {
                return "not a job site block";
            }
            if (holder.value() != poi.value()) {
                return "belongs to " + holder.getIdAsString();
            }
        }
        return null;
    }

    /// Extra facts appear below regardless of the verdict: even a broken profession gets them reported.
    private static Report finish(ServerCommandSource source, Report report, ProfessionDefinition definition,
                              RegistryEntry<PointOfInterestType> poi) {
        if (report.isBroken()) {
            report.verdict(false, "Broken at: " + report.firstBreak());
        } else if (definition.isOverride()) {
            report.verdict(true, overrideVerdict(definition));
            report.extra("job site", Text.literal(jobSiteOwner(definition.target(), poi))
                    .formatted(Formatting.GRAY));
        } else {
            // Distinct from `placed` (free blocks); names the block, what a player must go place.
            List<String> blocks = poi == null ? List.of() : jobSiteBlocks(poi);
            report.verdict(true, blocks.isEmpty()
                    ? "Villagers can take this job wherever one of its blocks is free."
                    : blocks.size() == 1
                            ? "Villagers can take this job at any free " + blocks.getFirst() + "."
                            : "Villagers can take this job at any free block of its job site: "
                                    + shortList(blocks) + ".");
        }

        texture(report, definition.texture(), definition.textureFile(), ProfessionLoader.directory(),
                definition.isOverride());
        hat(report, definition);
        if (poi != null) {
            placed(report, source, poi);
        }
        report.extra("trades", DataDrivenVillagersCommand.tradeStatus(definition));
        gift(report, source, definition);
        items(report, definition);
        schedule(report, definition);
        behaviour(report, definition);

        // Fields an override never reads: neither applied nor rejected, so they need naming.
        List<String> ignored = ProfessionLoader.ignoredFields(definition);
        if (!ignored.isEmpty()) {
            report.extra("ignored", Text.literal(String.join(", ", ignored)
                    + "  an override never reads these").formatted(Formatting.YELLOW));
        }

        if (definition.displayName().isEmpty() && !definition.isOverride()) {
            report.extra("name", Text.literal("no \"display_name\", so the villager is named by whatever "
                            + "a language file says for entity.minecraft.villager." + definition.name())
                    .formatted(Formatting.GRAY));
        }

        return report;
    }


    /// Summarizes what the override changes; the job site belongs to the overridden profession, reported separately.
    private static String overrideVerdict(ProfessionDefinition definition) {
        Identifier target = definition.target();
        List<String> does = new ArrayList<>();
        if (definition.texture().isPresent() || definition.textureFile().isPresent()
                || definition.zombieTexture().isPresent() || definition.zombieTextureFile().isPresent()
                || definition.hat() != HatKind.NONE) {
            does.add("looks");
        }
        List<String> behaviour = ProfessionLoader.behaviourFields(definition);
        if (!behaviour.isEmpty()) {
            does.add("behaves (" + String.join(", ", behaviour) + ")");
        }
        if (!definition.addWorkstations().isEmpty()) {
            does.add("works at more blocks");
        }

        if (does.isEmpty()) {
            // Every field it sets is one an override never reads; the "ignored" line names them.
            return definition.name() + ".json changes nothing about " + target + ".";
        }
        return definition.name() + ".json changes how " + target + " " + and(does) + ".";
    }

    /// `hat` mirrors vanilla's rule: `typeHatVisible = profession == NONE || (profession == PARTIAL && type != FULL)`.
    private static void hat(Report report, ProfessionDefinition definition) {
        String does = switch (definition.hat()) {
            case NONE -> "the hat of the type texture underneath stays visible";
            case PARTIAL -> "hides the hat of the type texture underneath on minecraft:desert and "
                    + "minecraft:snow, and on no other type - never something in between";
            case FULL -> "hides the hat of the type texture underneath, whatever the type";
        };
        report.extra("hat", Text.literal(definition.hat().name().toLowerCase(Locale.ROOT) + "  " + does)
                .formatted(definition.hat() == HatKind.PARTIAL ? Formatting.YELLOW : Formatting.GRAY));
        if (definition.hat() == HatKind.PARTIAL) {
            report.notes(List.of(new Note(true, "a villager type from this mod never has a hat of its "
                    + "own, so on those partial acts like none")));
        }
    }

    /// Names the blocks as well, because the job site id is not the block (`minecraft:farmer` is a composter).
    private static String jobSiteOwner(Identifier target, RegistryEntry<PointOfInterestType> poi) {
        if (poi == null) {
            return target + " has none of its own, and this file adds none";
        }
        return target + "'s own" + blocksOf(poi) + ", so villagers take the job the way they always did";
    }

    /// Blocks from POI_STATES_TO_TYPE, the sensor's map; a block another mod claimed is not listed here.
    private static List<String> jobSiteBlocks(RegistryEntry<PointOfInterestType> poi) {
        return PointOfInterestTypes.POI_STATES_TO_TYPE.entrySet().stream()
                .filter(entry -> entry.getValue().value() == poi.value())
                .map(entry -> Registries.BLOCK.getId(entry.getKey().getBlock()).toString())
                .distinct().sorted().toList();
    }

    /// Cut after three, so a line stays a line; `/ddv blocks` has the full list.
    private static String shortList(List<String> blocks) {
        if (blocks.size() <= 3) {
            return and(blocks);
        }
        return String.join(", ", blocks.subList(0, 3)) + " and " + (blocks.size() - 3) + " more";
    }

    /// In parentheses, for appending to a line that already says something.
    private static String blocksOf(RegistryEntry<PointOfInterestType> poi) {
        List<String> blocks = jobSiteBlocks(poi);
        return blocks.isEmpty() ? "" : " (" + shortList(blocks) + ")";
    }

    /// "a, b and c".
    private static String and(List<String> parts) {
        if (parts.size() == 1) {
            return parts.getFirst();
        }
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.getLast();
    }

    /// Blocks of this job site within 48 blocks of the player, with free places and who holds the rest.
    private static void placed(Report report, ServerCommandSource source, RegistryEntry<PointOfInterestType> poi) {
        BlockPos here = BlockPos.ofFloored(source.getPosition());
        List<PointOfInterest> stations = source.getWorld().getPointOfInterestStorage()
                .getInCircle(entry -> entry.value() == poi.value(), here, 48, PointOfInterestStorage.OccupationStatus.ANY)
                .toList();
        if (stations.isEmpty()) {
            // Names the block so the player knows what to place; "block of this job site" would not say.
            List<String> blocks = jobSiteBlocks(poi);
            report.extra("placed", Text.literal("no "
                            + (blocks.isEmpty() ? "block of this job site" : shortList(blocks))
                            + " within 48 blocks of you")
                    .formatted(Formatting.GRAY));
            return;
        }
        // Lines first: the summary's nobody note depends on what describe() counted.
        Holders holders = Holders.around(source.getWorld(), here);
        List<Note> notes = new ArrayList<>();
        stations.stream().limit(8).forEach(station -> notes.add(new Note(station.hasSpace(),
                station.getPos().toShortString() + "  " + holders.describe(station))));
        long withSpace = stations.stream().filter(PointOfInterest::hasSpace).count();
        report.extra("placed", Text.literal(stations.size() + " block(s) within 48 blocks of you, " + withSpace
                + " with a free place" + holders.nobodyNote())
                .formatted(withSpace == 0 ? Formatting.YELLOW : Formatting.GRAY));
        report.notes(notes, Formatting.GREEN);
    }

    /// Gatherable items and secondary job sites, including the values the parser fills in for `farm`.
    private static void items(Report report, ProfessionDefinition definition) {
        if (definition.isOverride()) {
            return;
        }
        if (!definition.gatherable().isEmpty()) {
            List<Note> notes = new ArrayList<>();
            for (Identifier item : definition.gatherable()) {
                boolean known = Registries.ITEM.containsId(item);
                notes.add(new Note(known, item + (known ? "" : "  no such item")));
            }
            report.extra("picks up", Text.literal(definition.gatherable().size() + " item type(s)"
                    + (definition.workBehaviour() == WorkBehaviour.FARM && definition.gatherable().equals(ProfessionParser.FARM_GATHERABLE)
                            ? ", the farmer's, filled in for farm" : "")).formatted(Formatting.GRAY));
            report.notes(notes);
        }
        if (!definition.secondarySites().isEmpty()) {
            List<Note> notes = new ArrayList<>();
            for (Identifier block : definition.secondarySites()) {
                boolean known = Registries.BLOCK.containsId(block);
                notes.add(new Note(known, block + (known ? "" : "  no such block")));
            }
            report.extra("also works at", Text.literal(definition.secondarySites().size() + " block type(s)"
                    + (definition.workBehaviour() == WorkBehaviour.FARM && definition.secondarySites().equals(ProfessionParser.FARM_SECONDARY_SITES)
                            ? ", farmland, filled in for farm" : "")).formatted(Formatting.GRAY));
            report.notes(notes);
        }
    }
    /// Only when the file sets a plan. A plan without a work entry never uses the job site.
    private static void schedule(Report report, ProfessionDefinition definition) {
        if (definition.schedule().isEmpty()) {
            return;
        }

        ScheduleDefinition plan = definition.schedule().get();
        boolean works = plan.entries().stream()
                .anyMatch(entry -> entry.activity() == ScheduleActivity.WORK);
        String what;
        Formatting colour;
        if (!works) {
            what = "no work in this plan, so the job site is never used";
            colour = Formatting.YELLOW;
        } else if (plan.worksAtNight()) {
            what = "works while it is dark";
            colour = Formatting.GRAY;
        } else {
            what = "works by day, as vanilla does";
            colour = Formatting.GRAY;
        }

        report.extra("schedule", Text.literal(plan.name() + "  " + what).formatted(colour));
        report.notes(plan.entries().stream()
                .map(entry -> new Note(true, entry.time() + "  " + entry.activity().lower()))
                .toList());
    }

    /// Work behaviour, fears, attack, health, villages: each only when the file sets it, with what it depends on.
    private static void behaviour(Report report, ProfessionDefinition definition) {
        if (definition.workBehaviour() == WorkBehaviour.FARM) {
            boolean farmland = definition.secondarySites().contains(Identifier.ofVanilla("farmland"));
            boolean seeds = definition.gatherable().stream().anyMatch(item -> item.getPath().endsWith("seeds"));
            List<String> missing = new ArrayList<>();
            if (!farmland) {
                missing.add("secondary_job_sites lacks minecraft:farmland, so the farm task never starts");
            }
            if (!seeds) {
                missing.add("gatherable_items has no seeds, so there is nothing to plant");
            }
            report.extra("work", Text.literal("farm  harvests and plants like the farmer"
                            + (missing.isEmpty() ? ", farmland within 8 blocks of the station needed"
                            : "  BUT " + String.join("; ", missing)))
                    .formatted(missing.isEmpty() ? Formatting.GRAY : Formatting.YELLOW));
        }
        if (definition.fears().isSet()) {
            List<Note> notes = new ArrayList<>();
            for (EntityRange fear : definition.fears().entries()) {
                boolean known = Registries.ENTITY_TYPE.containsId(fear.entity());
                notes.add(new Note(known, fear.describe() + (known ? "" : "  no such entity type")));
            }
            report.extra("flees from", Text.literal(definition.fears().describe())
                    .formatted(definition.fears().replacesVanilla() && definition.fears().entries().isEmpty()
                            ? Formatting.YELLOW : Formatting.GRAY));
            report.notes(notes);
        }
        definition.attack().ifPresent(attack -> {
            List<Note> notes = new ArrayList<>();
            for (EntityRange target : attack.targets()) {
                boolean known = Registries.ENTITY_TYPE.containsId(target.entity());
                boolean alsoFeared = definition.fears().of(target.entity()).isPresent();
                notes.add(new Note(known, target.describe() + (known ? "" : "  no such entity type")
                        + (alsoFeared ? "  also in flees_from, which loses: a target is never feared" : "")));
            }
            double health = definition.health().orElse(ProfessionDefinition.VANILLA_HEALTH);
            report.extra("attacks", Text.literal(attack.describe() + "  with " + health + " health and no armour"
                    + (definition.health().isEmpty() ? ", so two zombies win; \"health\" raises that" : ""))
                    .formatted(Formatting.GRAY));
            report.notes(notes);
        });
        definition.health().ifPresent(health -> report.extra("health", Text.literal(health + " instead of "
                + ProfessionDefinition.VANILLA_HEALTH + ", set whenever the brain is built").formatted(Formatting.GRAY)));
        if (!definition.villages().isEmpty()) {
            List<Note> notes = new ArrayList<>();
            for (Identifier village : definition.villages()) {
                boolean known = Registries.VILLAGER_TYPE.containsId(village);
                notes.add(new Note(known, village + (known ? "" : "  no such villager type")));
            }
            report.extra("villages", Text.literal("only villagers of these types take the job; others "
                    + "walk up to the block and turn away").formatted(Formatting.GRAY));
            report.notes(notes);
        }
    }

    private static void gift(Report report, ServerCommandSource source, ProfessionDefinition definition) {
        if (definition.gift().isEmpty()) {
            return;
        }

        Identifier id = definition.gift().get();
        LootTable table = source.getServer().getReloadableRegistries()
                .getLootTable(RegistryKey.of(RegistryKeys.LOOT_TABLE, id));
        // A missing loot table resolves to LootTable.EMPTY, never to an error.
        boolean usable = table != LootTable.EMPTY;
        report.extra("gift", Text.literal(id + (usable ? "" : "  no such loot table, or it is empty"))
                .formatted(usable ? Formatting.GRAY : Formatting.RED));
    }

    // ---------------------------------------------------------------- villager types

    /// Chain: type registered, named biomes (startup), tagged biomes (tag bind); reads BIOME_TO_TYPE live.
    static Report typeReport(ServerCommandSource source, TypeDefinition definition) {
        Report report = new Report();
        report.header(definition.id(), "villager type, from " + definition.name() + ".json");
        report.ok("file loaded", definition.name() + ".json");

        if (Registries.VILLAGER_TYPE.getOrEmpty(definition.id()).isEmpty()) {
            report.broken("type registered", definition.id() + " is not in the registry",
                    "registration failed after the file was read, /ddv errors has the reason.");
            report.skipped("named biomes claimed", "");
            report.skipped("biome tags claimed", "");
            report.verdict(false, "Broken at: " + report.firstBreak());
            return report;
        }
        report.ok("type registered", definition.id().toString());

        RegistryKey<VillagerType> key = RegistryKey.of(RegistryKeys.VILLAGER_TYPE, definition.id());
        Registry<Biome> biomes = source.getServer().getRegistryManager().get(RegistryKeys.BIOME);
        namedBiomes(report, definition, key, biomes);
        biomeTags(report, definition, key, biomes);

        long held = VillagerType.BIOME_TO_TYPE.values().stream()
                .filter(type -> definition.id().equals(Registries.VILLAGER_TYPE.getId(type))).count();
        if (report.isBroken()) {
            report.verdict(false, "Broken at: " + report.firstBreak());
        } else if (held == 0) {
            report.verdict(false, "No biome carries this type, so no villager is ever born with it.");
        } else {
            report.verdict(true, "Villagers born in " + held + " biome(s) get this type.");
        }

        texture(report, definition.texture(), definition.textureFile(), TypeLoader.directory(), false);
        return report;
    }

    private static void namedBiomes(Report report, TypeDefinition definition, RegistryKey<VillagerType> key,
                                    Registry<Biome> biomes) {
        if (definition.biomes().isEmpty()) {
            report.skipped("named biomes claimed", "this file names no biome outright");
            return;
        }

        List<Note> notes = new ArrayList<>();
        int claimed = 0;

        for (Identifier biome : definition.biomes()) {
            if (!biomes.containsId(biome)) {
                // An unknown biome is never rejected; its map entry is simply never looked up.
                notes.add(new Note(false, biome + "  no such biome in this world"));
                continue;
            }

            Identifier holder = holderOf(RegistryKey.of(RegistryKeys.BIOME, biome));
            if (key.getValue().equals(holder)) {
                claimed++;
                notes.add(new Note(true, biome + "  held"));
            } else {
                notes.add(new Note(false, biome + "  held by "
                        + (holder == null ? "nothing" : holder)));
            }
        }

        if (claimed == 0) {
            report.broken("named biomes claimed", "none of " + definition.biomes().size(),
                    "a biome named outright is supposed to win over anything, vanilla included. Losing "
                            + "one means something wrote into the map after us.");
        } else {
            report.ok("named biomes claimed", claimed + " of " + definition.biomes().size());
        }
        report.notes(notes);
    }

    /// @return the id of the villager type holding this biome, null when nothing holds it
    private static Identifier holderOf(RegistryKey<Biome> biome) {
        VillagerType holder = VillagerType.BIOME_TO_TYPE.get(biome);
        return holder == null ? null : Registries.VILLAGER_TYPE.getId(holder);
    }

    /// A tag claims only free biomes; a member still free means its hook did not run (others holding it is normal).
    private static void biomeTags(Report report, TypeDefinition definition, RegistryKey<VillagerType> key,
                                  Registry<Biome> biomes) {
        if (definition.biomeTags().isEmpty()) {
            report.skipped("biome tags claimed", "this file names no tag");
            return;
        }

        List<Note> notes = new ArrayList<>();
        boolean unresolved = false;

        for (Identifier tagId : definition.biomeTags()) {
            Optional<RegistryEntryList.Named<Biome>> tag =
                    biomes.getEntryList(TagKey.of(RegistryKeys.BIOME, tagId));
            if (tag.isEmpty()) {
                notes.add(new Note(false, "#" + tagId + "  no such biome tag"));
                unresolved = true;
                continue;
            }

            int members = 0;
            int held = 0;
            int free = 0;
            for (RegistryEntry<Biome> entry : tag.get()) {
                members++;
                Optional<RegistryKey<Biome>> biome = entry.getKey();
                if (biome.isEmpty()) {
                    continue;
                }
                Identifier holder = holderOf(biome.get());
                if (key.getValue().equals(holder)) {
                    held++;
                } else if (holder == null) {
                    free++;
                }
            }

            unresolved |= free > 0;
            notes.add(new Note(free == 0, "#" + tagId + "  " + held + " of " + members + " biome(s) held"
                    + (free > 0 ? ", " + free + " still free" : "")
                    + (held == 0 && free == 0 ? ", the rest held by others" : "")));
        }

        if (unresolved) {
            report.broken("biome tags claimed", "a tag did not reach this type",
                    "biome tags are read while a world loads. A biome inside the tag that is still free "
                            + "afterwards means the tag hook did not run for it.");
        } else {
            report.ok("biome tags claimed", definition.biomeTags().size() + " tag(s)");
        }
        report.notes(notes);
    }

    // ---------------------------------------------------------------- structures

    /// Chain: template readable, has a jigsaw block, is in the pools; without one, a piece is silently never placed.
    static Report structureReport(ServerCommandSource source, StructureDefinition definition) {
        Report report = new Report();
        report.header(definition.id(), "structure, from " + definition.name() + ".json");
        report.ok("file loaded", definition.name() + ".json");

        // A generated plot fails to load only when its workstation block does not exist.
        String building = definition.generated()
                ? "plot around " + definition.workstation().get()
                : definition.file().get();
        Optional<StructureTemplate> template =
                source.getServer().getStructureTemplateManager().getTemplate(definition.firstTemplateId());
        if (template.isEmpty()) {
            report.broken("nbt is readable", building + " did not load",
                    "the file is there but could not be read as a structure. It has to be one saved by "
                            + "a structure block, not a schematic from another program.");
            report.skipped("has a jigsaw block", "");
            report.skipped("is in the pools", "");
            report.verdict(false, "Broken at: " + report.firstBreak());
            return report;
        }
        // Not `toShortString`, which reads as coordinates.
        Vec3i size = template.get().getSize();
        report.ok("nbt is readable",
                building + ", " + size.getX() + "x" + size.getY() + "x" + size.getZ());

        int jigsaws = template.get()
                .getInfosForBlock(BlockPos.ORIGIN, new StructurePlacementData(), Blocks.JIGSAW).size();
        if (jigsaws == 0) {
            report.broken("has a jigsaw block", "none in the structure",
                    "a village piece connects through a jigsaw block, and without one the generator "
                            + "never places it and never says why. Put one in facing the street and "
                            + "give it the target of the pieces it should attach to.");
        } else {
            report.ok("has a jigsaw block", jigsaws + " jigsaw block(s)");
        }

        pools(report, source, definition);

        if (report.isBroken()) {
            report.verdict(false, "Broken at: " + report.firstBreak());
        } else {
            report.verdict(true, "This building can turn up in a village the game generates.");
        }

        report.extra("weight", Text.literal(String.valueOf(definition.weight())).formatted(Formatting.GRAY));
        villages(report, definition);
        report.extra("ground", Text.literal(definition.ground().name().toLowerCase(Locale.ROOT))
                .formatted(Formatting.GRAY));
        definition.processors().ifPresent(id ->
                report.extra("processors", Text.literal(id.toString()).formatted(Formatting.GRAY)));
        return report;
    }

    /// A saved nbt keeps its saved material in every village type; a generated plot's material is redrawn per village.
    private static void villages(Report report, StructureDefinition definition) {
        if (!definition.pools().isEmpty()) {
            // Pools named outright are listed by the pool step.
            return;
        }
        String list = String.join(", ", definition.villages());
        if (definition.generated()) {
            report.extra("villages", Text.literal(list + "  drawn in each village's own material")
                    .formatted(Formatting.GRAY));
        } else if (definition.villages().containsAll(StructureParser.ALL_VILLAGES)) {
            report.extra("villages", Text.literal("all five, in the material this nbt was saved in: a "
                            + "saved building is not redrawn per village, so name \"villages\" to keep it "
                            + "where it fits").formatted(Formatting.YELLOW));
        } else {
            report.extra("villages", Text.literal(list).formatted(Formatting.GRAY));
        }
    }

    /// Reads the world's live pools; they are rebuilt per world.
    private static void pools(Report report, ServerCommandSource source, StructureDefinition definition) {
        Registry<StructurePool> registry =
                source.getServer().getRegistryManager().get(RegistryKeys.TEMPLATE_POOL);

        List<Note> notes = new ArrayList<>();
        int found = 0;
        for (Identifier poolId : definition.targetPools()) {
            StructurePool pool = registry.getOrEmpty(poolId).orElse(null);
            if (pool == null) {
                notes.add(new Note(false, poolId + "  no such pool in this world"));
                continue;
            }
            int copies = countIn(pool, definition.templateId(poolId));
            if (copies == 0) {
                notes.add(new Note(false, poolId + "  not in it"));
            } else {
                found++;
                notes.add(new Note(true, poolId + "  " + copies + " of " + pool.getElementCount() + " draw(s)"));
            }
        }

        if (found == 0) {
            report.broken("is in the pools", "none of " + definition.targetPools().size(),
                    "the pools are filled when a world starts. If this stays empty, the file was added "
                            + "after this world loaded - /ddv reload puts it in.");
        } else {
            report.ok("is in the pools", found + " of " + definition.targetPools().size());
        }
        report.notes(notes);
    }

    /// Draws of this pool that land on the template: vanilla holds one element per point of weight.
    private static int countIn(StructurePool pool, Identifier id) {
        int copies = 0;
        for (StructurePoolElement element : pool.elements) {
            if (!(element instanceof SinglePoolElement single)) {
                continue;
            }
            // An element built from a template instead of an id (another mod's) has no id.
            if (single.location.left().filter(id::equals).isPresent()) {
                copies++;
            }
        }
        return copies;
    }

    // ---------------------------------------------------------------- shared

    /// @param optional true for an override, which keeps the overridden profession's texture
    private static void texture(Report report, Optional<Identifier> identifier, Optional<String> file,
                                Path folder, boolean optional) {
        if (identifier.isPresent()) {
            report.extra("texture", Text.literal(identifier.get() + "  from a resource pack")
                    .formatted(Formatting.GRAY));
            return;
        }
        if (file.isEmpty()) {
            report.extra("texture", Text.literal(optional
                            ? "none, the overridden profession keeps its own"
                            : "none, villagers render with the missing texture")
                    .formatted(optional ? Formatting.GRAY : Formatting.RED));
            return;
        }

        // Resolved through ConfigFiles so this never answers "is there a file at" for a path outside the folder.
        Optional<Path> png = ConfigFiles.resolveInside(folder, file.get()).filter(Files::isRegularFile);
        if (png.isEmpty()) {
            report.extra("texture", Text.literal(file.get() + "  NOT in " + folder)
                    .formatted(Formatting.RED));
            return;
        }

        // Both verdicts LookSync reaches, in order, shown here since an author cannot see the server log.
        Optional<String> refused = headerRejection(png.get());
        if (refused.isPresent()) {
            report.extra("texture", Text.literal(file.get() + "  " + refused.get()
                    + ", so no client draws it").formatted(Formatting.RED));
            return;
        }

        // Not fatal: the image is fine, only too big to ship, so a player who installed the pack still sees it.
        OptionalLong tooLarge = oversize(png.get());
        if (tooLarge.isPresent()) {
            report.extra("texture", Text.literal(file.get() + "  " + tooLarge.getAsLong()
                    + " bytes, above the " + LookSync.MAX_PNG_BYTES + " the server sends, so only players "
                    + "with their own copy see it").formatted(Formatting.YELLOW));
            return;
        }

        report.extra("texture", Text.literal(file.get() + "  next to the json").formatted(Formatting.GRAY));
    }

    /// @return why no client will decode this image, empty when it is fine or cannot be read here
    private static Optional<String> headerRejection(Path png) {
        try {
            return PngHeader.rejection(png);
        } catch (IOException e) {
            return Optional.of("could not be read: " + e.getMessage());
        }
    }

    /// @return the size when it is above what {@link LookSync} sends, empty otherwise
    private static OptionalLong oversize(Path png) {
        try {
            long size = Files.size(png);
            return size > LookSync.MAX_PNG_BYTES ? OptionalLong.of(size) : OptionalLong.empty();
        } catch (IOException e) {
            return OptionalLong.empty();
        }
    }

    /// Not loaded: reports the rejection reason if a file of that name was rejected.
    private static int unknown(ServerCommandSource source, Identifier asked) {
        String file = asked.getPath() + ".json";

        Optional<String> profession = ProfessionRegistry.errors().stream()
                .filter(error -> error.file().equalsIgnoreCase(file))
                .map(ProfessionRegistry.LoadError::reason)
                .findFirst();
        if (profession.isPresent()) {
            return rejected(source, asked, "profession file, rejected", profession.get());
        }

        Optional<String> type = TypeRegistry.errors().stream()
                .filter(error -> error.file().equalsIgnoreCase(file))
                .map(TypeRegistry.LoadError::reason)
                .findFirst();
        if (type.isPresent()) {
            return rejected(source, asked, "villager type file, rejected", type.get());
        }

        Optional<String> structure = StructureRegistry.errors().stream()
                .filter(error -> error.file().equalsIgnoreCase(file))
                .map(StructureRegistry.LoadError::reason)
                .findFirst();
        if (structure.isPresent()) {
            return rejected(source, asked, "structure file, rejected", structure.get());
        }

        source.sendError(Text.literal("Nothing called " + asked + " is loaded. /ddv list shows what is, "
                + "/ddv errors which files were rejected."));
        return 0;
    }

    private static int rejected(ServerCommandSource source, Identifier asked, String kind, String reason) {
        Report report = new Report();
        report.header(asked, kind);
        report.broken("file loaded", reason,
                "fix the file, then restart the game. Professions and types are registered before any "
                        + "world exists, so /reload cannot pick this up.");
        report.verdict(false, "Broken at: " + report.firstBreak());
        return report.send(source);
    }
}
