package com.lion.datadrivenvillagers.profession;

import com.lion.datadrivenvillagers.DataDrivenVillagers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/// Writes a working example, its png and a readme into the professions folder when it is empty.
final class ExampleProfession {

    private static final String EXAMPLE_FILE = "example_baker.json";
    private static final String README_FILE = "README.txt";
    private static final String EXAMPLE_TEXTURE_FILE = "example_baker.png";

    /// Shipped in the jar and copied next to the json, the same way an author's png is read.
    private static final String EXAMPLE_TEXTURE_RESOURCE =
            "/assets/datadrivenvillagers/example/example_baker.png";

    /// Package private so the test parses it with the same rules an author's file gets.
    static final String EXAMPLE = """
            {
              "workstation": "minecraft:campfire",
              "display_name": "Baker",
              "texture": "example_baker.png",
              "hat": "none",
              "work_sound": "minecraft:entity.villager.work_farmer",
              "gatherable_items": ["minecraft:wheat", "minecraft:bread"],
              "secondary_job_sites": ["minecraft:cake"],
              "ticket_count": 1,
              "search_distance": 1
            }
            """;

    private static final String README = """
            DataDrivenVillagers
            ===================

            Every .json file in this folder becomes one villager profession. The file name is the id:
            example_baker.json becomes the profession datadrivenvillagers:example_baker.

            Why is this not a datapack?
            ---------------------------
            Villager professions and points of interest live in registries that Minecraft freezes
            before it reads any datapack. A datapack is simply too late to create them. That is why
            this folder is read while the game starts. Changes need a restart, not /reload.

            Changing a profession that already exists
            ----------------------------------------
            Name it under "overrides" and this file modifies that profession instead of creating one.
            Works for vanilla and for other mods:

                { "overrides": "minecraft:farmer", "texture": "my_farmer.png", "hat": "full" }

            Texture, hat and gift are all it takes. To give the profession another workstation on top
            of the one it has, use "add_workstations". Do not use "workstation" there: that one
            creates a job site, and the two must not be confused.

            Fields
            ------
            overrides            an existing profession this file modifies instead of adding one
            add_workstations     extra blocks for an overridden profession's existing job site,
                                 only together with "overrides"
            workstation          required unless overriding, a block id or a list of block ids.
                                 The block must not
                                 already be a job site: smoker, barrel, blast_furnace, brewing_stand,
                                 cartography_table, cauldron, composter, fletching_table, grindstone,
                                 lectern, loom, smithing_table and stonecutter are taken by vanilla
                                 professions and are rejected.
            display_name         shown when no language file translates the profession
            texture              a file name next to this json (baker.png) or a full identifier
                                 into a resource pack
            hat                  none, partial or full. It does not decide how this profession
                                 looks - its own image covers the head either way. It decides whether
                                 the hat of the villager TYPE texture underneath is drawn. Only
                                 minecraft:desert and minecraft:snow have one, both "full", so
                                 "partial" acts like "full" on those two and like "none" on every
                                 other type. /ddv why <profession> says it in the game.
            work_sound           sound id played while working
            gatherable_items     items the villager picks up
            secondary_job_sites  extra blocks the villager is drawn to
            gift                 loot table thrown at a Hero of the Village after a raid. Vanilla
                                 keeps its gift list hardcoded, so without this a villager of yours
                                 throws the unemployed gift, which is a single wheat seed.
            schedule             the day plan, see below. Leave it out to keep vanilla's.
            zombie_texture       the zombie villager's own image, same rule as texture. Left out,
                                 the zombie wears "texture".
            work_behaviour       station (default) or farm, see Behaviour below
            flees_from           entity ids this profession runs from, on top of vanilla's list,
                                 or { "entity": ..., "distance": ... } for a distance of its own
            attacks              entity ids the villager goes after, same shape as flees_from
            attack               { "damage": 2, "cooldown": 20 }, only together with attacks
            health               max health, default 20
            villages             villager types allowed to take the job, e.g. ["desert"]. Left
                                 out, any villager may.
            ticket_count         how many villagers work at one station at the same time, default 1
            search_distance      how far a villager looks for the station, default 1

            Day plans
            ---------
            Vanilla gives every adult villager the same day: idle at sunrise, work from 2000, meet
            at the bell from 9000, sleep from 12000. "schedule" changes that per profession.

                { "workstation": "minecraft:campfire", "schedule": "night" }

            "night" is that same plan half a day later, so the villager sleeps through the day,
            wakes at dusk, works the night and gathers at the bell before sunrise. "default" is the
            vanilla plan, worth naming when you want to say so out loud.

            Or write the switch-over points yourself. Each entry says what the villager does from
            that tick until the next one, and the last entry carries over midnight into the first:

                "schedule": [
                  { "time": 0,     "activity": "rest" },
                  { "time": 13000, "activity": "work" },
                  { "time": 22000, "activity": "idle" }
                ]

            A day is 0 to 23999 ticks, 0 is sunrise and 12000 is sunset. Four activities: idle,
            work, meet and rest. No others, and that is on purpose: core runs always, and panic,
            raid, pre_raid and hide are started by what a villager senses rather than by the clock.
            An activity outside those four leaves the brain with nothing to run, so the villager
            would simply stand still.

            This is the one field that also works with "overrides", because a day plan is looked up
            per villager instead of being baked into the profession when it is created. So you can
            put the vanilla farmer on the night shift, which nothing else in this file can do.

            It is one of two fields a /ddv reload carries all the way to villagers that already
            exist in the world; the other is work_behaviour.

            Behaviour
            ---------
            Three fields are looked up per villager, like the plan, so they follow a reload at once
            and work with "overrides" too.

            "work_behaviour": "farm" gives the profession the farmer's routine: walk the farmland
            nearby, harvest what is ripe, plant what is in the inventory, bone meal what is slow.
            It needs farmland in secondary_job_sites and seeds in gatherable_items; leave both out
            and the farmer's own values are filled in. Not for an override of anything but the
            farmer, whose secondary sites are frozen at registration.

            "flees_from": ["minecraft:creeper"] adds to the eleven things every villager fears, it
            never removes one. A bare id means eight blocks, an object names its own distance.
            "flees_only_from": [...] replaces the list instead; [] is a villager that fears nothing
            on sight. One of the two per file, not both.

            "attacks": ["minecraft:zombie"] makes the villager fight instead of flee: walk up, hit
            every 20 ticks for 2 damage, forget the target when it is gone. Whatever is in attacks
            is never feared. 20 health, no armour: one zombie loses, two win.

            "villages": ["desert"] reserves the job for villagers of those types. Others walk up
            to the block and turn away. /ddv why villager on one of them says so.

            Textures
            --------
            Put baker.png next to baker.json and set "texture": "baker.png". No resource pack needed.
            The image is a villager profession overlay, same layout as the vanilla ones: only the
            robe area is painted and everything else stays transparent. example_baker.png beside
            this readme is a plain apron to start from.

            Leaving "texture" out does not give an untextured villager, it gives one wearing the
            missing texture, because Minecraft still looks the id up and finds nothing. The log says
            so on startup.

            The three things this mod does not read
            ---------------------------------------
            Trades belong to VillagerTradingPlus, the gift is a loot table and belongs to a datapack,
            and the name belongs to a resource pack. Each is a format you would otherwise have to go
            and learn somewhere else first, so:

                /ddv scaffold example_baker

            writes all three into professions/scaffold/example_baker/, each with a note saying where
            in a pack it belongs. It never overwrites what you edited there.

                /ddv export example_baker

            packs the profession, its png and those three files into one zip somebody else can
            unpack. Anything in it that was generated rather than written by you is marked as such,
            in the report and again in the readme inside the zip.

            Check /ddv list and /ddv errors in game to see what loaded and what did not, and
            /ddv why example_baker when something loaded but nothing happens.
            """;

    private ExampleProfession() {
    }

    static void writeIfFolderIsEmpty(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            if (stream.findAny().isPresent()) {
                return;
            }
        } catch (IOException e) {
            return;
        }

        write(dir.resolve(EXAMPLE_FILE), EXAMPLE);
        write(dir.resolve(README_FILE), README);
        copyFromJar(EXAMPLE_TEXTURE_RESOURCE, dir.resolve(EXAMPLE_TEXTURE_FILE));
    }

    private static void write(Path file, String content) {
        try {
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            DataDrivenVillagers.LOGGER.warn("Could not write {}", file, e);
        }
    }

    /// The example json names this file; without it the example villager wears the missing texture.
    private static void copyFromJar(String resource, Path file) {
        try (InputStream in = ExampleProfession.class.getResourceAsStream(resource)) {
            if (in == null) {
                DataDrivenVillagers.LOGGER.warn("Example texture {} is not in the jar, {} stays empty",
                        resource, file);
                return;
            }
            Files.copy(in, file);
        } catch (IOException e) {
            DataDrivenVillagers.LOGGER.warn("Could not write {}", file, e);
        }
    }
}
