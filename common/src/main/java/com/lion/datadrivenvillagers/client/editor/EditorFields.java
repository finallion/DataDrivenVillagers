package com.lion.datadrivenvillagers.client.editor;

import com.lion.datadrivenvillagers.profession.Attack;
import com.lion.datadrivenvillagers.profession.EntityRange;
import com.lion.datadrivenvillagers.profession.ProfessionDefinition;
import com.lion.datadrivenvillagers.profession.ProfessionLoader;
import com.lion.datadrivenvillagers.profession.ProfessionParser;

import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.world.poi.PointOfInterestTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/// What every field is, in one place: its name, what happens when it is left alone, what it means, and
/// where the values it accepts come from.
public final class EditorFields {

    /// What a value looks like, which decides both the widget and how it is written back.
    public enum Shape {
        TEXT, LIST, NUMBER, RANGES
    }

    /// Where the suggestions under a box come from. The client holds all of these registries, so no
    /// packet is needed to answer "which blocks are there" - and the loader's own question, which
    /// blocks are still free, needs a map this mod fills on both sides.
    public enum Source {
        NONE, FREE_BLOCK, BLOCK, ENTITY, ITEM, SOUND, VILLAGER_TYPE, PROFESSION
    }

    /// @param key         the json key, or empty for a row that is not a plain field
    /// @param label       what stands to the left of the box, and what is hovered to read the help
    /// @param shape       how the value is read and written
    /// @param source      what the suggestions under the box are drawn from
    /// @param placeholder greyed out in an empty box: the fallback as prose, or an example after e.g.
    /// @param help        shown on hovering the label, first line saying what the field does
    public record Spec(String key, String label, Shape shape, Source source, String placeholder,
                       String help, boolean frozen) {

        Spec(String key, String label, Shape shape, Source source, String placeholder, String help) {
            this(key, label, shape, source, placeholder, help, false);
        }

        /// The six fields that live in the `VillagerProfession` or `PointOfInterestType` record. A
        /// reload cannot touch them.
        Spec needsRestart() {
            return new Spec(key, label + " *", shape, source, placeholder,
                    help + "\n\nChanging this on a profession that already exists needs a restart: it "
                            + "was handed to the game when the profession was registered, and a reload "
                            + "cannot reach it.", true);
        }
    }

    private static List<Identifier> freeBlocks;

    private EditorFields() {
    }

    public static final List<Spec> BASICS = List.of(
            new Spec("", "File name", Shape.TEXT, Source.NONE, "e.g. stonecutter",
                    """
                    Names the file and the profession inside it.
                    Lower case letters, digits, underscore and dash - a profession id is
                    always lower case, so the file follows it. No .json, that is added."""),
            new Spec("overrides", "Overrides", Shape.TEXT, Source.PROFESSION, "empty means a new profession",
                    """
                    Changes a profession that already exists instead of making a new one.
                    Put minecraft:farmer here and every farmer in the world follows this file.
                    Leave it empty and this file creates a profession of its own.

                    An override may only change what the game asks per villager: the day plan,
                    work behaviour, what it runs from, what it attacks, health and the look.
                    Workstation, sound and trades were fixed when the game started."""),
            new Spec("workstation", "Workstation", Shape.LIST, Source.FREE_BLOCK, "e.g. minecraft:calcite",
                    """
                    The block a villager stands at to take this job. One is enough, more are
                    allowed - separate them with commas.
                    A block another job site already owns cannot be taken over. The list under
                    the box holds only free ones, and a taken one turns red as you type."""),
            new Spec("add_workstations", "Adds workstations", Shape.LIST, Source.FREE_BLOCK, "only used by an override",
                    """
                    Gives the overridden profession extra blocks to work at.
                    Only read when Overrides is set. Its villagers then work at their own block
                    and at these."""),
            new Spec("display_name", "Display name", Shape.TEXT, Source.NONE, "falls back to the raw key",
                    """
                    The name a player sees in the trade screen.
                    Without it, and without a language file, the raw translation key shows up
                    there instead.""").needsRestart(),
            new Spec("ticket_count", "Ticket count", Shape.NUMBER, Source.NONE,
                    String.valueOf(ProfessionParser.DEFAULT_TICKET_COUNT),
                    """
                    How many villagers may share one block of this kind.
                    Above 1 is worth testing before you ship it: a villager takes its place the
                    moment it sets off, not when it arrives, so places can end up held by
                    nobody.""").needsRestart());

    public static final List<Spec> LOOK = List.of(
            new Spec("texture", "Texture", Shape.TEXT, Source.NONE, "e.g. stonecutter.png",
                    """
                    The image the villager wears. Two forms are accepted.
                      a png lying next to this json, named outright: stonecutter.png
                      a resource pack path: namespace:textures/entity/villager/profession/x.png
                    The png needs no resource pack - it is read at runtime and sent to every
                    player who joins."""),
            new Spec("zombie_texture", "Zombie texture", Shape.TEXT, Source.NONE, "wears the texture above",
                    """
                    The image for the zombie villager of this profession.
                    Left empty it wears the one above, which is what vanilla does."""),
            new Spec("work_sound", "Work sound", Shape.TEXT, Source.SOUND, "silent",
                    """
                    Played while the villager works at its block.
                    An override cannot change this - the sound was baked into the profession
                    when the game started.""").needsRestart(),
            new Spec("gift", "Gift loot table", Shape.TEXT, Source.NONE, "vanilla decides",
                    """
                    Thrown at a player who is Hero of the Village.
                    A loot table id, which lives in a datapack rather than in this folder -
                    /ddv scaffold writes one to start from."""));

    public static final List<Spec> WORK = List.of(
            new Spec("gatherable_items", "Picks up items", Shape.LIST, Source.ITEM, "picks up nothing",
                    """
                    Items this villager takes off the ground. Items only, separated by commas.
                    The farm behaviour needs its seeds named here, or the villager harvests
                    once and then stands in an empty field with nothing to plant.""").needsRestart(),
            new Spec("secondary_job_sites", "Second job sites", Shape.LIST, Source.BLOCK, "none",
                    """
                    Blocks the villager treats as a job site besides its own.
                    Left empty together with the farm behaviour, the parser fills in
                    farmland.""").needsRestart(),
            new Spec("villages", "Village types", Shape.LIST, Source.VILLAGER_TYPE, "any type",
                    """
                    Which kinds of villager may take this job. Empty means all of them.
                    One of the wrong kind walks up, turns away, and leaves the block free for
                    one that fits."""),
            new Spec("health", "Health", Shape.NUMBER, Source.NONE,
                    String.valueOf((int) ProfessionDefinition.VANILLA_HEALTH),
                    """
                    Maximum health, in half hearts. Vanilla gives every villager 20.
                    Applied on every job change, so a villager that loses this job drops back
                    to 20."""),
            new Spec("search_distance", "Search distance", Shape.NUMBER, Source.NONE,
                    String.valueOf(ProfessionParser.DEFAULT_SEARCH_DISTANCE),
                    """
                    How far the villager looks for its block, counted in chunk sections.
                    Every vanilla profession uses 1, which is why that is the default.""").needsRestart());

    public static final List<Spec> COMBAT = List.of(
            new Spec("attacks", "Attacks", Shape.RANGES, Source.ENTITY, "attacks nothing",
                    """
                    What this villager walks up to and hits when it sees one.
                    Add @distance for how far away it notices one:
                      minecraft:zombie@10, minecraft:skeleton
                    Without @ it uses """ + EntityRange.DEFAULT_DISTANCE + """
                     blocks.
                    A villager that also runs from what it attacks will run - the fear wins."""),
            new Spec("damage", "Attack damage", Shape.NUMBER, Source.NONE,
                    String.valueOf((int) Attack.DEFAULT_DAMAGE),
                    """
                    How hard it hits, in half hearts.
                    Only read when Attacks names something. Empty this and the cooldown and
                    the whole attack block leaves the file."""),
            new Spec("cooldown", "Attack cooldown", Shape.NUMBER, Source.NONE,
                    String.valueOf(Attack.DEFAULT_COOLDOWN),
                    """
                    Ticks between two swings. Twenty ticks is one second."""));

    public static final Spec FEAR_LIST = new Spec("", "Runs from these", Shape.RANGES, Source.ENTITY,
            "e.g. minecraft:creeper@12",
            """
            What it runs from on sight, with @distance as above.
            Only read when the button above is set to add to, or replace, vanilla's list.
            Panic from actually being hurt comes from elsewhere and stays either way.""");

    public static final Spec PLAN_ENTRIES = new Spec("", "Written out", Shape.TEXT, Source.NONE,
            "e.g. 0=rest, 14000=work, 21000=meet",
            """
            Your own day plan, as tick=activity separated by commas.
            Activities: idle, work, meet, rest.
            An entry runs until the next one starts, and the last carries over midnight into
            the first. 0 is sunrise, 12000 sunset.""");

    // ---- suggestions ----------------------------------------------------------------------------

    /// Ids that match what has been typed so far, best first, at most a screenful.
    ///
    /// Matched on the path as well as on the whole id, because an author types `calcite` far more
    /// often than `minecraft:calcite`, and a list that only answers the second is one that never opens.
    public static List<String> suggest(Source source, String typed) {
        String needle = lastPart(typed).toLowerCase(Locale.ROOT);
        List<String> starts = new ArrayList<>();
        List<String> contains = new ArrayList<>();

        for (Identifier id : ids(source)) {
            String full = id.toString();
            if (needle.isEmpty() || full.startsWith(needle) || id.getPath().startsWith(needle)) {
                starts.add(full);
            } else if (full.contains(needle)) {
                contains.add(full);
            }
            if (starts.size() >= 40) {
                break;
            }
        }
        starts.addAll(contains);
        return starts.size() > 7 ? starts.subList(0, 7) : starts;
    }

    /// A list field holds several values, and only the one being typed is being completed.
    public static String lastPart(String typed) {
        int comma = typed.lastIndexOf(',');
        return comma < 0 ? typed.trim() : typed.substring(comma + 1).trim();
    }

    private static List<Identifier> ids(Source source) {
        return switch (source) {
            case NONE -> List.of();
            case FREE_BLOCK -> freeBlocks();
            case BLOCK -> List.copyOf(Registries.BLOCK.getIds());
            case ENTITY -> List.copyOf(Registries.ENTITY_TYPE.getIds());
            case ITEM -> List.copyOf(Registries.ITEM.getIds());
            case SOUND -> List.copyOf(Registries.SOUND_EVENT.getIds());
            case VILLAGER_TYPE -> List.copyOf(Registries.VILLAGER_TYPE.getIds());
            case PROFESSION -> List.copyOf(Registries.VILLAGER_PROFESSION.getIds());
        };
    }

    /// Every block no job site has claimed yet - the question `/ddv blocks` answers, asked while the
    /// author types instead of after the reload.
    private static List<Identifier> freeBlocks() {
        if (freeBlocks == null) {
            List<Identifier> free = new ArrayList<>();
            for (Identifier id : Registries.BLOCK.getIds()) {
                Block block = Registries.BLOCK.getOrEmpty(id).orElse(null);
                if (block != null && ProfessionLoader.existingOwner(
                        PointOfInterestTypes.getStatesOfBlock(block)).isEmpty()) {
                    free.add(id);
                }
            }
            freeBlocks = free;
        }
        return freeBlocks;
    }

    /// Who already owns this block, if anybody. What turns "rejected on save" into a red value under
    /// the cursor while there is still something to change.
    public static Optional<String> ownerOf(String blockId) {
        Identifier id = Identifier.tryParse(blockId.trim());
        if (id == null) {
            return Optional.empty();
        }
        return Registries.BLOCK.getOrEmpty(id)
                .flatMap(block -> ProfessionLoader.existingOwner(PointOfInterestTypes.getStatesOfBlock(block)));
    }

    /// True for an id that names nothing in the block registry, so the screen can say so before the
    /// save does.
    public static boolean unknownBlock(String blockId) {
        Identifier id = Identifier.tryParse(blockId.trim());
        return id == null || Registries.BLOCK.getOrEmpty(id).isEmpty();
    }

    /// Dropped when a world is left, because what is free depends on what that world had registered.
    public static void forget() {
        freeBlocks = null;
    }
}
