package com.lion.datadrivenvillagers.command;

import com.lion.datadrivenvillagers.mixin.MerchantEntityAccessor;
import com.lion.datadrivenvillagers.profession.Attack;
import com.lion.datadrivenvillagers.profession.EntityRange;
import com.lion.datadrivenvillagers.profession.ProfessionBehaviours;
import com.lion.datadrivenvillagers.profession.ProfessionDefinition;
import com.lion.datadrivenvillagers.profession.ProfessionLoader;
import com.lion.datadrivenvillagers.profession.ScheduleDefinition;
import com.lion.datadrivenvillagers.profession.ScheduleParser;
import com.lion.datadrivenvillagers.profession.WorkBehaviour;
import com.lion.datadrivenvillagers.type.TypeDefinition;
import com.lion.datadrivenvillagers.type.TypeRegistry;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.ZombieVillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameRules;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.VillagerType;
import net.minecraft.world.biome.Biome;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.registry.tag.PointOfInterestTypeTags;
import net.minecraft.world.poi.PointOfInterest;
import net.minecraft.world.poi.PointOfInterestStorage;
import net.minecraft.world.poi.PointOfInterestType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/// `/ddv why villager [<target>]`: what one villager holds right now, mostly read out of its brain
/// memories: job, station, bed, plan versus current activity, sleep blockers, pickup state.
final class VillagerWhy {

    private static final double NEAREST_RADIUS = 16;

    private VillagerWhy() {
    }

    static LiteralArgumentBuilder<ServerCommandSource> node() {
        return CommandManager.literal("villager")
                .executes(Framed.framed(VillagerWhy::nearest))
                .then(CommandManager.argument("target", EntityArgumentType.entity())
                        .executes(Framed.framed(VillagerWhy::targeted)));
    }

    private static int nearest(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        Vec3d at = source.getPosition();
        Box box = Box.of(at, NEAREST_RADIUS * 2, NEAREST_RADIUS * 2, NEAREST_RADIUS * 2);
        // Nearest villager or zombie villager.
        LivingEntity closest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity candidate : source.getWorld().getEntitiesByClass(LivingEntity.class, box,
                entity -> entity instanceof VillagerEntity || entity instanceof ZombieVillagerEntity)) {
            double distance = candidate.squaredDistanceTo(at);
            if (distance < best) {
                best = distance;
                closest = candidate;
            }
        }
        if (closest == null) {
            source.sendError(Text.literal("No villager within " + (int) NEAREST_RADIUS + " blocks. Stand next "
                    + "to one, or name it: /ddv why villager @e[type=villager,limit=1,sort=nearest]"));
            return 0;
        }
        return reportAny(source, closest).send(source);
    }

    private static int targeted(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        Entity entity = EntityArgumentType.getEntity(context, "target");
        if (!(entity instanceof VillagerEntity) && !(entity instanceof ZombieVillagerEntity)) {
            source.sendError(Text.literal(entity.getType().getName().getString() + " is not a villager."));
            return 0;
        }
        return reportAny(source, (LivingEntity) entity).send(source);
    }

    private static Report reportAny(ServerCommandSource source, LivingEntity entity) {
        return entity instanceof VillagerEntity villager ? report(source, villager)
                : zombie(source, (ZombieVillagerEntity) entity);
    }

    /// A zombie villager keeps profession, type, level, xp, trades and the tickets on its old station
    /// and bed, but not the memories of them: a zombie's brain has no module for job site or bed.
    /// `releaseAllTickets` runs only on death and on the witch conversion, so a cured villager starts
    /// with an empty brain and the old blocks stay taken.
    static Report zombie(ServerCommandSource source, ZombieVillagerEntity zombie) {
        Report report = new Report();
        ServerWorld world = (ServerWorld) zombie.getWorld();
        BlockPos here = zombie.getBlockPos();
        VillagerProfession profession = zombie.getVillagerData().getProfession();
        Optional<ProfessionDefinition> definition = ProfessionBehaviours.of(profession);
        Identifier typeId = Registries.VILLAGER_TYPE.getId(zombie.getVillagerData().getType());

        report.header("zombie villager " + zombie.getUuid().toString().substring(0, 8) + " at " + here.toShortString(),
                (zombie.isBaby() ? "baby, " : "") + typeId + ", level " + zombie.getVillagerData().getLevel()
                        + ", " + zombie.getXp() + " xp, " + (int) zombie.getHealth() + "/" + (int) zombie.getMaxHealth() + " health");

        if (profession == VillagerProfession.NONE) {
            report.skipped("profession", "none, it had no job when it was bitten");
        } else {
            report.ok("profession", idOf(profession) + definition.map(d -> "  from " + d.name() + ".json").orElse("  vanilla")
                    + ", kept through the bite and through a cure");
            workstation(report, profession);
        }
        report.warn("job site and bed", "not remembered: a zombie's brain has no module for them. The places on the "
                + "old blocks stay taken, and a cured villager starts with an empty brain and never finds them again "
                + "- break and replace those blocks");
        report.extra("curing", Text.literal(zombie.isConverting()
                ? "in progress, it becomes a villager in a few minutes"
                : "not started: weakness effect, then a golden apple").formatted(Formatting.GRAY));
        stationsNearbyOf(report, world, here, null);
        return report;
    }

    static Report report(ServerCommandSource source, VillagerEntity villager) {
        Report report = new Report();
        ServerWorld world = (ServerWorld) villager.getWorld();
        Brain<VillagerEntity> brain = villager.getBrain();
        BlockPos here = villager.getBlockPos();

        VillagerProfession profession = villager.getVillagerData().getProfession();
        Identifier professionId = idOf(profession);
        Optional<ProfessionDefinition> definition = ProfessionBehaviours.of(profession);
        Identifier typeId = Registries.VILLAGER_TYPE.getId(villager.getVillagerData().getType());

        report.header("villager " + villager.getUuid().toString().substring(0, 8) + " at " + here.toShortString(),
                (villager.isBaby() ? "baby, " : "") + typeId + ", level " + villager.getVillagerData().getLevel()
                        + ", " + villager.getExperience() + " xp, " + (int) villager.getHealth() + "/" + (int) villager.getMaxHealth() + " health");

        // Vanilla professions without a file of ours are reported too.
        if (villager.isBaby()) {
            report.skipped("profession", "a baby has none, and plays until it grows up");
        } else if (profession == VillagerProfession.NONE) {
            report.warn("profession", "none  looking for a job site, or none within reach");
        } else if (profession == VillagerProfession.NITWIT) {
            report.warn("profession", "nitwit  never takes a job, by vanilla's rule");
        } else {
            report.ok("profession", professionId + definition.map(d -> "  from " + d.name() + ".json"
                    + (d.isOverride() ? " (override)" : "")).orElse("  vanilla, no file of ours"));
            workstation(report, profession);
        }
        type(report, world, villager, typeId);

        trades(report, villager, profession);

        place(report, world, brain, MemoryModuleType.JOB_SITE, "job site", here,
                villager.isBaby() ? "" : "none claimed. A villager without one cannot work; "
                        + "/ddv why <profession> checks whether the block can be found at all.");
        brain.getOptionalMemory(MemoryModuleType.POTENTIAL_JOB_SITE).ifPresent(potential ->
                report.extra("eyeing", Text.literal(potential.getPos().toShortString() + ", "
                        + distance(here, potential) + " blocks, will take it on arrival" + refusal(villager, world, potential))
                        .formatted(Formatting.GRAY)));
        place(report, world, brain, MemoryModuleType.HOME, "bed", here,
                "none claimed. Without a bed the rest activity only walks indoors, and it wakes at any noise.");
        place(report, world, brain, MemoryModuleType.MEETING_POINT, "bell", here, "");

        stationsNearby(report, world, villager);
        schedule(report, world, brain, definition, villager);
        activity(report, brain, villager);
        sleep(report, world, brain, villager);
        attack(report, villager, definition);
        gathers(report, world, villager, brain, definition);
        memories(report, world, brain);
        behaviour(report, definition);

        return report;
    }

    /// The villager type: its file, its texture, and whether the biome the villager stands in is held
    /// by this type by name or through a tag. The type is set at birth from the birth biome, which is
    /// not remembered afterwards, so the current biome is the only witness.
    private static void type(Report report, ServerWorld world, VillagerEntity villager, Identifier typeId) {
        Optional<TypeDefinition> definition = typeId == null ? Optional.empty() : TypeRegistry.get(typeId);
        if (definition.isEmpty()) {
            report.ok("type", typeId + "  vanilla, no file of ours");
            return;
        }
        TypeDefinition d = definition.get();
        String texture = d.texture().map(id -> id + " from a resource pack")
                .or(() -> d.textureFile().map(file -> file + " next to the json"))
                .orElse("none");
        report.ok("type", typeId + "  from " + d.name() + ".json, texture " + texture);

        RegistryEntry<Biome> biome = world.getBiome(villager.getBlockPos());
        Optional<RegistryKey<Biome>> key = biome.getKey();
        if (key.isEmpty()) {
            return;
        }
        Identifier biomeId = key.get().getValue();
        VillagerType holder = VillagerType.BIOME_TO_TYPE.get(key.get());
        String how;
        if (holder != null && Registries.VILLAGER_TYPE.getId(holder).equals(typeId)) {
            if (d.biomes().contains(biomeId)) {
                how = "held by this type, named outright in the file";
            } else {
                how = "held by this type through " + d.biomeTags().stream()
                        .filter(tag -> biome.isIn(TagKey.of(RegistryKeys.BIOME, tag)))
                        .map(tag -> "#" + tag).findFirst().orElse("a tag");
            }
        } else {
            how = "held by " + (holder == null ? "no type" : Registries.VILLAGER_TYPE.getId(holder))
                    + ", so it was born elsewhere or given the type by hand";
        }
        report.notes(List.of(new Note(true, "biome here " + biomeId + "  " + how)));
    }

    /// Why a villager refuses to trade. Two vanilla traps that stack:
    /// 1. `getOffers()` builds the offer list on the first right click and caches it; `interactMob`
    ///    answers an empty list with `sayNo`. An empty list is not written to nbt, so unloading the
    ///    chunk clears it. Read here through an accessor so the call itself does not trigger the build.
    /// 2. `fillRecipes` hands out only the tier of the villager's current level, nothing below it.
    ///    A summoned `level:2` villager with only novice trades gets nothing, and re-entering the
    ///    world rebuilds the same nothing. Summon with level 1 plus `Xp:1` (xp 0 loses the job).
    private static void trades(Report report, VillagerEntity villager,
                               VillagerProfession profession) {
        if (villager.isBaby() || profession == VillagerProfession.NONE
                || profession == VillagerProfession.NITWIT) {
            return;
        }
        int level = villager.getVillagerData().getLevel();
        Int2ObjectMap<TradeOffers.Factory[]> map = TradeOffers.PROFESSION_TO_LEVELED_TRADE.get(profession);
        int total = map == null ? 0
                : map.values().stream().mapToInt(factories -> factories.length).sum();
        TradeOffers.Factory[] tier = map == null ? null : map.get(level);
        int forLevel = tier == null ? 0 : tier.length;
        TradeOfferList held = ((MerchantEntityAccessor) villager).ddv$offersOrNull();

        if (held == null && total == 0) {
            report.warn("trades", "none for this profession, and none built yet. Careful: the "
                    + "first right click builds his list from that nothing and he keeps it - "
                    + "bring the trades in before anyone clicks him");
        } else if (held == null && forLevel == 0) {
            report.warn("trades", total + " trade(s) for this profession, but NONE for his level "
                    + level + " - the first right click hands him only his own level's tier and "
                    + "caches the nothing he gets. Fill that tier, or summon at level 1 with Xp:1 "
                    + "(the Xp keeps the job)");
        } else if (held == null) {
            report.ok("trades", forLevel + " trade(s) waiting for his level; he builds his list "
                    + "on the first right click");
        } else if (held.isEmpty()) {
            if (forLevel > 0) {
                report.warn("trades", "his offer list is cached EMPTY: it was built when his "
                        + "level's tier had nothing, and vanilla never asks again. His tier has "
                        + forLevel + " trade(s) by now, and the empty list is not saved: unload "
                        + "the chunk or re-enter the world, then click him again");
            } else {
                report.warn("trades", "his offer list is cached EMPTY, and his level " + level
                        + " tier is STILL empty" + (total > 0 ? " (" + total + " trade(s) sit on "
                        + "other levels)" : "") + " - re-entering the world only rebuilds the "
                        + "same nothing. Fill his tier, or summon at level 1 with Xp:1");
            }
        } else {
            report.ok("trades", held.size() + " offer(s) held"
                    + (total == 0 ? ", built before the profession's trades went away" : ""));
        }
    }

    private static void place(Report report, ServerWorld world, Brain<VillagerEntity> brain,
                              MemoryModuleType<GlobalPos> memory, String what, BlockPos here, String ifMissing) {
        Optional<GlobalPos> pos = brain.getOptionalMemory(memory);
        if (pos.isEmpty()) {
            if (ifMissing.isEmpty()) {
                report.skipped(what, "none");
            } else {
                report.warn(what, ifMissing);
            }
            return;
        }
        String where = pos.get().getPos().toShortString() + ", " + distance(here, pos.get()) + " blocks";
        if (!pos.get().getDimension().equals(world.getRegistryKey())) {
            report.warn(what, where + ", in " + pos.get().getDimension().getValue() + ", another dimension");
            return;
        }
        BlockState state = world.getBlockState(pos.get().getPos());
        Identifier block = Registries.BLOCK.getId(state.getBlock());
        Optional<RegistryEntry<PointOfInterestType>> poi = world.getPointOfInterestStorage().getType(pos.get().getPos());
        if (poi.isEmpty()) {
            if (memory == MemoryModuleType.MEETING_POINT) {
                // The core task list forgets a job site, the rest list a bed, nothing forgets a meeting
                // point: a moved bell leaves the old spot in memory until the next meet. Harmless.
                report.warn(what, where + ", " + block + " is no bell any more, stale: vanilla never "
                        + "forgets a meeting point, the next meet finds the new bell");
                return;
            }
            report.broken(what, where + ", " + block + " is no point of interest any more",
                    "the block was removed or replaced. The villager notices within a few seconds and "
                            + "forgets it; a worker loses the job with it.");
            return;
        }
        report.ok(what, where + ", " + block + ", " + tickets(world, pos.get().getPos(), poi.get()));
    }


    /// Free tickets on a point of interest. A villager removed without dying never returns its ticket;
    /// only breaking and replacing the block frees it.
    private static String tickets(ServerWorld world, BlockPos pos, RegistryEntry<PointOfInterestType> poi) {
        int free = world.getPointOfInterestStorage().getFreeTickets(pos);
        int total = poi.value().ticketCount();
        return free + "/" + total + " place(s) free";
    }

    /// Acquirable job site blocks within 48 blocks with their free places. A villager with a job cannot
    /// switch, so for one only its own block is listed and the rest counted.
    private static void stationsNearby(Report report, ServerWorld world, VillagerEntity villager) {
        if (villager.isBaby()) {
            return;
        }
        stationsNearbyOf(report, world, villager.getBlockPos(), villager);
    }

    /// @param villager the one asking, for the `villages` check and the job filter; null for a zombie villager
    private static void stationsNearbyOf(Report report, ServerWorld world, BlockPos here, VillagerEntity villager) {
        Optional<RegistryEntry<PointOfInterestType>> own = villager == null ? Optional.empty() : ownJobSite(villager);
        List<PointOfInterest> all = world.getPointOfInterestStorage()
                .getInCircle(poi -> poi.isIn(PointOfInterestTypeTags.ACQUIRABLE_JOB_SITE), here,
                        48, PointOfInterestStorage.OccupationStatus.ANY)
                .sorted(Comparator.comparingDouble(poi -> poi.getPos().getSquaredDistance(here)))
                .toList();
        long leftOut = own.map(site -> all.stream().filter(poi -> !sameType(poi, site)).count()).orElse(0L);
        List<PointOfInterest> stations = all.stream()
                .filter(poi -> own.map(site -> sameType(poi, site)).orElse(true))
                .limit(8)
                .toList();
        String others = leftOut == 0 ? "" : ", " + leftOut + " of other jobs left out";
        if (stations.isEmpty()) {
            report.extra("stations", Text.literal("no " + own.map(site -> ProfessionLoader.idOf(site) + " ").orElse("")
                    + "job site block within 48 blocks" + others).formatted(Formatting.GRAY));
            return;
        }
        // Per line who holds the places; the explanation for "held by nobody" once, in the summary.
        Holders holders = Holders.around(world, here);
        List<Note> notes = new ArrayList<>();
        int withSpace = 0;
        for (PointOfInterest station : stations) {
            boolean space = station.hasSpace();
            if (space) {
                withSpace++;
            }
            Optional<String> refusal = villager == null ? Optional.empty() : ProfessionBehaviours.refusal(villager, station.getType());
            notes.add(new Note(space, ProfessionLoader.idOf(station.getType()) + " at " + station.getPos().toShortString()
                    + ", " + String.format(Locale.ROOT, "%.1f", Math.sqrt(station.getPos().getSquaredDistance(here)))
                    + " blocks, " + holders.describe(station)
                    + refusal.map(r -> "  not for this villager: " + r).orElse("")));
        }
        report.extra("stations", Text.literal(stations.size() + " within 48 blocks, " + withSpace + " with a free place"
                        + others + holders.nobodyNote())
                .formatted(withSpace == 0 ? Formatting.YELLOW : Formatting.GRAY));
        report.notes(notes, Formatting.GREEN);
    }
    private static String distance(BlockPos from, GlobalPos to) {
        return String.format(Locale.ROOT, "%.1f", Math.sqrt(from.getSquaredDistance(to.getPos())));
    }

    private static String refusal(VillagerEntity villager, ServerWorld world, GlobalPos potential) {
        ServerWorld siteWorld = world.getServer().getWorld(potential.getDimension());
        if (siteWorld == null) {
            return "";
        }
        return siteWorld.getPointOfInterestStorage().getType(potential.getPos())
                .flatMap(poi -> ProfessionBehaviours.refusal(villager, poi))
                .map(reason -> " - NO, it will be turned away: " + reason)
                .orElse("");
    }

    /// The schedule the brain holds and what it says for the current hour.
    private static void schedule(Report report, ServerWorld world, Brain<VillagerEntity> brain,
                                 Optional<ProfessionDefinition> definition, VillagerEntity villager) {
        int time = (int) (world.getTimeOfDay() % ScheduleParser.DAY_LENGTH);
        Activity planned = brain.getSchedule().getActivityForTime(time);
        String name;
        if (villager.isBaby()) {
            name = "vanilla baby plan";
        } else {
            name = definition.flatMap(ProfessionDefinition::schedule).map(ScheduleDefinition::name)
                    .map(plan -> plan + " (from the file)")
                    .orElse("vanilla default");
        }
        report.extra("plan", Text.literal(name + "  at " + time + " it says " + activityName(planned))
                .formatted(Formatting.GRAY));
    }

    private static void activity(Report report, Brain<VillagerEntity> brain, VillagerEntity villager) {
        Optional<Activity> current = brain.getFirstPossibleNonCoreActivity();
        int time = (int) (villager.getWorld().getTimeOfDay() % ScheduleParser.DAY_LENGTH);
        Activity planned = brain.getSchedule().getActivityForTime(time);
        String doing = current.map(VillagerWhy::activityName).orElse("nothing but core");
        boolean jobless = villager.getVillagerData().getProfession() == VillagerProfession.NONE
                || villager.getVillagerData().getProfession() == VillagerProfession.NITWIT;
        if (current.isPresent() && current.get() != planned && jobless && activityName(planned).equals("work")) {
            // Vanilla idles a jobless villager during "work".
            report.extra("doing", Text.literal(doing + "  the plan says work, but there is no job to do").formatted(Formatting.GRAY));
        } else if (current.isPresent() && current.get() != planned) {
            String why = switch (activityName(current.get())) {
                case "panic" -> "something hurt it or a hostile is near, see below";
                case "raid", "pre_raid" -> "a raid is on, and that outranks the clock";
                case "hide" -> "the bell rang, it hides until the bell is quiet";
                default -> "the plan switches within a second, so this is about to change";
            };
            report.extra("doing", Text.literal(doing + "  not what the plan says: " + why).formatted(Formatting.YELLOW));
        } else {
            report.extra("doing", Text.literal(doing + (villager.isSleeping() ? ", asleep" : "")).formatted(Formatting.GRAY));
        }
    }

    /// The conditions of vanilla's `SleepTask.shouldRun`, each reported separately: a bed in this
    /// dimension, within 2 blocks, still a bed, not occupied, and not woken in the last 100 ticks.
    private static void sleep(Report report, ServerWorld world, Brain<VillagerEntity> brain, VillagerEntity villager) {
        if (villager.isBaby()) {
            return;
        }
        List<String> blockers = new ArrayList<>();
        Optional<GlobalPos> home = brain.getOptionalMemory(MemoryModuleType.HOME);
        if (home.isEmpty()) {
            blockers.add("no bed claimed");
        } else {
            if (!home.get().getDimension().equals(world.getRegistryKey())) {
                blockers.add("bed is in another dimension");
            } else {
                BlockState state = world.getBlockState(home.get().getPos());
                if (!state.isIn(BlockTags.BEDS)) {
                    blockers.add("the claimed bed is not a bed any more");
                } else if (state.get(BedBlock.OCCUPIED)) {
                    blockers.add("the bed is occupied");
                }
                if (!home.get().getPos().isWithinDistance(villager.getPos(), 2.0)) {
                    blockers.add("bed is " + distance(villager.getBlockPos(), home.get()) + " blocks away, has to be within 2");
                }
            }
        }
        brain.getOptionalMemory(MemoryModuleType.LAST_WOKEN).ifPresent(woken -> {
            long since = world.getTime() - woken;
            if (since < 100) {
                blockers.add("woke " + since + " ticks ago, will not lie down again for " + (100 - since));
            }
        });
        if (villager.hasVehicle()) {
            blockers.add("riding something");
        }

        if (villager.isSleeping()) {
            report.extra("sleep", Text.literal("asleep now").formatted(Formatting.GRAY));
        } else if (blockers.isEmpty()) {
            report.extra("sleep", Text.literal("may lie down the moment the plan says rest").formatted(Formatting.GRAY));
        } else {
            // Yellow only while the plan says rest.
            int time = (int) (world.getTimeOfDay() % ScheduleParser.DAY_LENGTH);
            boolean restNow = activityName(brain.getSchedule().getActivityForTime(time)).equals("rest");
            report.extra("sleep", Text.literal((restNow ? "cannot right now: " : "could not right now, but the plan says "
                    + activityName(brain.getSchedule().getActivityForTime(time)) + ": ") + String.join("; ", blockers))
                    .formatted(restNow ? Formatting.YELLOW : Formatting.GRAY));
        }
    }


    /// Attack damage read off the entity attribute, which is set when the brain is built, so it can
    /// lag behind the file.
    private static void attack(Report report, VillagerEntity villager, Optional<ProfessionDefinition> definition) {
        Optional<Attack> attack = definition.flatMap(ProfessionDefinition::attack);
        if (attack.isEmpty()) {
            return;
        }
        double damage = villager.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        boolean asSet = damage == attack.get().damage();
        String targets = attack.get().targets().stream().map(EntityRange::describe).collect(Collectors.joining(", "));
        report.extra("attack", Text.literal(damage + " damage every " + attack.get().cooldown() + " ticks, goes after "
                + targets + (asSet ? "" : "  the file says " + attack.get().damage() + ", the brain was not rebuilt since"))
                .formatted(asSet ? Formatting.GRAY : Formatting.YELLOW));
    }
    /// Pickup state, read off the `VillagerProfession` record rather than the file: `gatherable_items`
    /// is handed over once at registration, so file and game can disagree until a restart. Vanilla
    /// also needs room in the inventory and no pickup cooldown.
    private static void gathers(Report report, ServerWorld world, VillagerEntity villager,
                                Brain<VillagerEntity> brain, Optional<ProfessionDefinition> definition) {
        Set<Item> inGame = villager.getVillagerData().getProfession().gatherableItems();
        List<Identifier> inFile = definition.map(ProfessionDefinition::gatherable).orElse(List.of());
        if (inGame.isEmpty() && inFile.isEmpty()) {
            return;
        }

        Set<Identifier> running = inGame.stream().map(Registries.ITEM::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        String names = running.isEmpty() ? "nothing"
                : running.stream().map(Identifier::toString).collect(Collectors.joining(", "));
        boolean same = running.equals(new LinkedHashSet<>(inFile));
        report.extra("picks up", Text.literal(names + (same ? "" : "  the file says " + inFile
                        + ", which only reaches the game after a restart"))
                .formatted(same ? Formatting.GRAY : Formatting.YELLOW));

        int free = 0;
        for (int slot = 0; slot < villager.getInventory().size(); slot++) {
            if (villager.getInventory().getStack(slot).isEmpty()) {
                free++;
            }
        }
        List<String> blocked = new ArrayList<>();
        // `MobEntity.readCustomData` sets CanPickUpLoot from nbt with default false, overwriting the
        // constructor's true: a summoned villager never picks up.
        if (!villager.canPickUpLoot()) {
            blocked.add("its CanPickUpLoot flag is off, so it can never take anything - loading a "
                    + "villager from nbt sets that flag to false, which is what /summon does. Fix it "
                    + "with /data merge entity <target> {CanPickUpLoot:1b}");
        }
        // doMobGriefing is checked after the item list; off, the villager walks to the item and stops.
        if (!world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) {
            blocked.add("the doMobGriefing game rule is off, and while it is no mob picks up anything "
                    + "at all - it is checked after the item list, so wanting an item changes nothing");
        }
        if (free == 0) {
            blocked.add("its inventory is full, and a full villager picks up nothing at all");
        }
        brain.getOptionalMemory(MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS)
                .ifPresent(ticks -> blocked.add("it is on a pickup cooldown for another " + ticks + " ticks"));
        if (!blocked.isEmpty()) {
            report.extra("but", Text.literal(String.join("; ", blocked)).formatted(Formatting.YELLOW));
        }

        brain.getOptionalMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM).ifPresent(item -> {
            // Vanilla's pickup box: the bounding box widened 1 block sideways and 0 upwards.
            boolean reachable = villager.getBoundingBox().expand(1.0, 0.0, 1.0)
                    .intersects(item.getBoundingBox());
            double up = item.getY() - villager.getY();
            report.extra("wants", Text.literal(item.getStack().getCount() + "x "
                    + Registries.ITEM.getId(item.getStack().getItem()) + ", "
                    + String.format(Locale.ROOT, "%.1f", item.distanceTo(villager)) + " blocks away, "
                    + String.format(Locale.ROOT, "%.1f", Math.abs(up))
                    + (up < 0 ? " below" : " above") + " its feet").formatted(Formatting.GRAY));

            if (!reachable) {
                report.extra("cannot reach", Text.literal("a villager only reaches into its own "
                        + "bounding box widened by one block sideways and none upwards, so this item "
                        + "is out of reach where it lies. Put it on the block it stands on.")
                        .formatted(Formatting.YELLOW));
            } else if (item.cannotPickup()) {
                report.extra("cannot reach", Text.literal("the item still has a pickup delay running")
                        .formatted(Formatting.YELLOW));
            }
        });
    }

    private static void memories(Report report, ServerWorld world, Brain<VillagerEntity> brain) {
        long now = world.getTime();
        List<String> said = new ArrayList<>();
        brain.getOptionalMemory(MemoryModuleType.LAST_SLEPT).ifPresent(t -> said.add("slept " + ago(now, t)));
        brain.getOptionalMemory(MemoryModuleType.LAST_WOKEN).ifPresent(t -> said.add("woke " + ago(now, t)));
        brain.getOptionalMemory(MemoryModuleType.LAST_WORKED_AT_POI).ifPresent(t -> said.add("worked " + ago(now, t)));
        brain.getOptionalMemory(MemoryModuleType.HEARD_BELL_TIME).ifPresent(t -> said.add("heard the bell " + ago(now, t)));
        if (!said.isEmpty()) {
            report.extra("last", Text.literal(String.join(", ", said)).formatted(Formatting.GRAY));
        }

        List<String> danger = new ArrayList<>();
        brain.getOptionalMemory(MemoryModuleType.HURT_BY).ifPresent(source -> danger.add("hurt by " + source.getName()));
        brain.getOptionalMemory(MemoryModuleType.HURT_BY_ENTITY).ifPresent(entity -> danger.add("hurt by " + describe(entity)));
        brain.getOptionalMemory(MemoryModuleType.NEAREST_HOSTILE).ifPresent(entity -> danger.add("hostile near: " + describe(entity)));
        brain.getOptionalMemory(MemoryModuleType.ATTACK_TARGET).ifPresent(entity -> danger.add("going after " + describe(entity)));
        if (brain.getOptionalMemory(MemoryModuleType.GOLEM_DETECTED_RECENTLY).orElse(false)) {
            danger.add("saw a golem recently");
        }
        if (!danger.isEmpty()) {
            report.extra("danger", Text.literal(String.join(", ", danger)).formatted(Formatting.YELLOW));
        }
    }

    private static void behaviour(Report report, Optional<ProfessionDefinition> definition) {
        if (definition.isEmpty()) {
            return;
        }
        ProfessionDefinition d = definition.get();
        List<String> said = new ArrayList<>();
        if (d.workBehaviour() == WorkBehaviour.FARM) {
            said.add("farms");
        }
        if (d.fears().isSet()) {
            said.add("fears " + d.fears().describe());
        }
        d.attack().ifPresent(attack -> said.add("attacks " + attack.describe()));
        d.health().ifPresent(health -> said.add("health " + health));
        if (!d.villages().isEmpty()) {
            said.add("job only for " + d.villages());
        }
        if (!said.isEmpty()) {
            report.extra("file says", Text.literal(String.join(", ", said) + "  /ddv why " + d.name() + " has the detail")
                    .formatted(Formatting.GRAY));
        }
    }

    private static String describe(LivingEntity entity) {
        return Registries.ENTITY_TYPE.getId(entity.getType()) + " at " + entity.getBlockPos().toShortString();
    }

    private static String ago(long now, long then) {
        long ticks = now - then;
        if (ticks < 1200) {
            return ticks + " ticks ago";
        }
        return String.format(Locale.ROOT, "%.1f min ago", ticks / 1200.0);
    }

    private static String activityName(Activity activity) {
        return Registries.ACTIVITY.getId(activity) == null ? activity.toString() : Registries.ACTIVITY.getId(activity).getPath();
    }

    private static Identifier idOf(VillagerProfession profession) {
        Identifier id = Registries.VILLAGER_PROFESSION.getId(profession);
        return id == null ? new Identifier("unknown") : id;
    }

    /// The blocks the registered job site accepts, as the game holds them now.
    private static void workstation(Report report, VillagerProfession profession) {
        String blocks = ProfessionLoader.jobSiteOf(profession)
                .map(poi -> poi.value().blockStates().stream()
                        .map(state -> Registries.BLOCK.getId(state.getBlock()).toString())
                        .distinct().sorted().collect(Collectors.joining(", ")))
                .orElse("");
        if (blocks.isEmpty()) {
            report.warn("workstation", "no block registered for this job site");
        } else {
            report.ok("workstation", blocks);
        }
    }

    private static Optional<RegistryEntry<PointOfInterestType>> ownJobSite(VillagerEntity villager) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (profession == VillagerProfession.NONE || profession == VillagerProfession.NITWIT) {
            return Optional.empty();
        }
        return ProfessionLoader.jobSiteOf(profession);
    }

    private static boolean sameType(PointOfInterest poi, RegistryEntry<PointOfInterestType> site) {
        return poi.getType().getKey().equals(site.getKey());
    }
}
