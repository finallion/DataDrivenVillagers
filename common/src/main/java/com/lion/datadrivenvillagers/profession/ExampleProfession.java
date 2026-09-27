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
              "workstation": "minecraft:structure_block",
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

            The example uses a structure block as its workstation, so it takes no block away from a
            normal world. To try it, get one with /give @s minecraft:structure_block and place it
            next to an unemployed villager.

            Why this is not a datapack
            --------------------------
            Villager professions and points of interest are registries. Minecraft freezes them before
            it reads any datapack, so a datapack cannot create them. The mod reads this folder at
            startup. A new or removed file needs a restart, not /reload.

            Changing a profession that already exists
            -----------------------------------------
            Name it under "overrides". The file then changes that profession instead of creating a
            new one. This works for vanilla professions and for professions from other mods:

                { "overrides": "minecraft:farmer", "texture": "my_farmer.png", "hat": "full" }

            To give an existing profession more workstation blocks, use "add_workstations". Do not
            use "workstation" in an override: "workstation" creates a new job site.

            Fields
            ------
            overrides            an existing profession this file changes instead of adding one
            add_workstations     extra blocks for an overridden profession's existing job site,
                                 only together with "overrides"
            workstation          required unless overriding, a block id or a list of block ids.
                                 The block must not already be a job site: smoker, barrel,
                                 blast_furnace, brewing_stand, cartography_table, cauldron, composter,
                                 fletching_table, grindstone, lectern, loom, smithing_table and
                                 stonecutter belong to vanilla professions and are rejected.
            display_name         shown when no language file translates the profession
            texture              a file name next to this json (baker.png) or a full identifier
                                 into a resource pack
            hat                  none, partial or full. It does not change this profession's image,
                                 which always covers the head. It decides whether the hat of the
                                 villager TYPE texture underneath is drawn. Only minecraft:desert and
                                 minecraft:snow have one, both "full", so "partial" works like "full"
                                 on those two and like "none" on all other types.
                                 /ddv why <profession> shows it in the game.
            work_sound           sound id played while working
            gatherable_items     items the villager picks up
            secondary_job_sites  extra blocks the villager is drawn to
            gift                 loot table thrown at a Hero of the Village after a raid. Without it,
                                 the villager throws the unemployed gift, a single wheat seed.
            schedule             the day plan, see below. Leave it out to keep vanilla's.
            zombie_texture       the zombie villager's own image, same rule as texture. Left out,
                                 the zombie uses "texture".
            work_behaviour       station (default) or farm, see Behaviour below
            flees_from           entity ids this profession runs from, on top of vanilla's list,
                                 or { "entity": ..., "distance": ... } for a distance of its own
            attacks              entity ids the villager attacks, same form as flees_from
            attack               { "damage": 2, "cooldown": 20 }, only together with attacks
            health               max health, default 20
            villages             villager types allowed to take the job, e.g. ["desert"]. Left
                                 out, all types can.
            ticket_count         how many villagers work at one station at the same time, default 1
            search_distance      how far a villager looks for the station, default 1

            Day plans
            ---------
            Vanilla gives every adult villager the same day: idle at sunrise, work from 2000, meet
            at the bell from 9000, sleep from 12000. "schedule" changes that per profession.

                { "workstation": "minecraft:structure_block", "schedule": "night" }

            "night" is the same plan shifted by half a day: the villager sleeps during the day,
            wakes at dusk, works at night and meets at the bell before sunrise. "default" is the
            vanilla plan.

            You can also write the switch-over points yourself. Each entry sets the activity from
            its tick until the next entry. The last entry continues past midnight into the first:

                "schedule": [
                  { "time": 0,     "activity": "rest" },
                  { "time": 13000, "activity": "work" },
                  { "time": 22000, "activity": "idle" }
                ]

            A day is 0 to 23999 ticks, 0 is sunrise and 12000 is sunset. Four activities: idle,
            work, meet and rest. Other activities are rejected: core always runs, and panic, raid,
            pre_raid and hide start from what a villager senses, not from the clock.

            "schedule" also works with "overrides", because the day plan is looked up per villager.
            Example: put the vanilla farmer on the night shift.

            /ddv reload applies two fields also to villagers already in the world: schedule and
            work_behaviour.

            Behaviour
            ---------
            These fields are looked up per villager, like the day plan. They apply at once after a
            reload and work with "overrides".

            "work_behaviour": "farm" gives the profession the farmer's routine: walk the farmland
            nearby, harvest ripe crops, plant seeds from the inventory, use bone meal. It needs
            farmland in secondary_job_sites and seeds in gatherable_items. If both are left out, the
            farmer's values are used. An override can use farm only for the farmer.

            "flees_from": ["minecraft:creeper"] adds to the eleven entity types every villager runs
            from. It never removes one. A bare id means eight blocks; an object sets its own
            distance. "flees_only_from": [...] replaces the list; [] means the villager runs from
            nothing it sees. Use one of the two per file, not both.

            "attacks": ["minecraft:zombie"] makes the villager attack instead of flee: walk up, hit
            every 20 ticks for 2 damage, forget the target when it is gone. The villager never runs
            from an entity in attacks. With 20 health and no armour, it beats one zombie but not two.

            "villages": ["desert"] allows the job only for villagers of those types. Other villagers
            ignore the block. /ddv why villager shows this for one villager.

            Textures
            --------
            Put baker.png next to baker.json and set "texture": "baker.png". No resource pack needed.
            The image is a villager profession overlay with the same layout as the vanilla ones: only
            the robe area is painted, everything else is transparent. example_baker.png next to this
            readme is a plain apron to start from.

            Without "texture", the villager shows the missing texture, because Minecraft still looks
            up the id and finds nothing. The log shows a warning at startup.

            Trades, gift and name
            ---------------------
            Trades come from VillagersTradingPlus, the gift is a loot table in a datapack, and the
            name is a translation in a resource pack.

                /ddv scaffold example_baker

            writes all three into professions/scaffold/example_baker/. Each file has a note that
            says where it belongs in a pack. The command never overwrites a file you edited there.

                /ddv export example_baker

            packs the profession, its png and those three files into one zip for other players.
            Files that the mod generated are marked in the report and in the readme inside the zip.

            /ddv list and /ddv errors show what loaded and what did not. /ddv why example_baker
            checks a profession that loaded but does not work in the game.
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
