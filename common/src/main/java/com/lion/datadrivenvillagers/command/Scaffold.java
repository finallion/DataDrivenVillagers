package com.lion.datadrivenvillagers.command;

import com.lion.datadrivenvillagers.DataDrivenVillagers;
import com.lion.datadrivenvillagers.profession.ProfessionDefinition;
import com.lion.datadrivenvillagers.profession.ProfessionLoader;
import com.lion.datadrivenvillagers.structure.PlotGenerator;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/// Generates the files a profession needs outside this mod: VillagerTradingPlus trades, a gift loot
/// table (datapack), a language file (resource pack) and a village stall. One generator shared by
/// `/ddv scaffold`, `/ddv export` and the editor's trades button.
public final class Scaffold {

    public static final String FOLDER = "scaffold";

    private static final String TRADES = "trades.json";
    private static final String GIFT = "gift.json";
    private static final String LANG = "en_us.json";

    private Scaffold() {
    }

    /// @param file        name inside the scaffold folder
    /// @param destination path inside the pack this belongs to
    /// @param content     ready to write, filled in from the definition
    public record Piece(String file, String destination, String content) {
    }

    public static Path folderFor(ProfessionDefinition definition) {
        return ProfessionLoader.directory().resolve(FOLDER).resolve(definition.name());
    }

    public static List<Piece> pieces(ProfessionDefinition definition) {
        Identifier gift = giftId(definition);
        return List.of(
                tradesPiece(definition),
                new Piece(GIFT, "datapack/data/" + gift.getNamespace()
                        + "/loot_table/" + gift.getPath() + ".json", gift(definition)),
                new Piece(LANG, "resourcepack/assets/minecraft/lang/" + LANG, lang(definition)));
    }

    /// Separate because the editor's trades button writes exactly this piece into the world's datapacks.
    public static Piece tradesPiece(ProfessionDefinition definition) {
        return new Piece(TRADES, "datapack/data/" + DataDrivenVillagers.MOD_ID
                + "/default_villager_trades/" + definition.name() + ".json", trades(definition));
    }

    /// The `"gift"` id, or the id the file would need. The datapack path is derived from it.
    public static Identifier giftId(ProfessionDefinition definition) {
        return definition.gift().orElseGet(() ->
                DataDrivenVillagers.id("gameplay/hero_of_the_village/" + definition.name() + "_gift"));
    }

    /// Vanilla's key for a villager's name, `entity.minecraft.villager.<path>` regardless of namespace.
    /// Built from the target so an override writes the overridden profession's key.
    public static String translationKey(ProfessionDefinition definition) {
        return "entity.minecraft.villager." + definition.target().getPath();
    }

    private static String trades(ProfessionDefinition definition) {
        String target = definition.target().toString();
        return """
                {
                  "_comment": "VillagerTradingPlus trades for %s. The profession is read from the \\"profession\\" key below, not from this file's name. default_villager_trades replaces the profession's set, villager_trades adds to it. A villager is handed only the tier of the level he is on when first clicked - a summoned level:2 villager sees apprentice, not novice.",
                  "profession": "%s",
                  "trades": {
                    "novice": [
                      {
                        "type": "villagertradingplus:sell_item",
                        "sell": { "item": "minecraft:bread", "count": 6 },
                        "priceIn": { "item": "minecraft:emerald", "count": 1 },
                        "max_uses": 16,
                        "villager_experience": 1
                      },
                      {
                        "type": "villagertradingplus:buy_item",
                        "buy": { "item": "minecraft:wheat", "count": 20 },
                        "reward": { "item": "minecraft:emerald", "count": 1 },
                        "max_uses": 16,
                        "villager_experience": 1
                      }
                    ],
                    "apprentice": [],
                    "journeyman": [],
                    "expert": [],
                    "master": []
                  }
                }
                """.formatted(target, target);
    }

    /// Vanilla's gift format, shaped like `gameplay/hero_of_the_village/farmer_gift.json`, seeded with
    /// the first gatherable item.
    private static String gift(ProfessionDefinition definition) {
        Identifier id = giftId(definition);
        String item = definition.gatherable().isEmpty()
                ? "minecraft:bread"
                : definition.gatherable().get(0).toString();
        return """
                {
                  "_comment": "Thrown at a player with Hero of the Village after a raid. The profession file has to name this table in its gift field.",
                  "type": "minecraft:gift",
                  "random_sequence": "%s",
                  "pools": [
                    {
                      "rolls": 1.0,
                      "bonus_rolls": 0.0,
                      "entries": [
                        {
                          "type": "minecraft:item",
                          "name": "%s"
                        }
                      ]
                    }
                  ]
                }
                """.formatted(id, item);
    }

    /// Written even with a `display_name`: that is the fallback for a missing translation, this is the
    /// translation.
    private static String lang(ProfessionDefinition definition) {
        String name = definition.displayName().orElseGet(() -> readable(definition.target().getPath()));
        return """
                {
                  "%s": "%s"
                }
                """.formatted(translationKey(definition), name);
    }

    private static String readable(String path) {
        StringBuilder out = new StringBuilder();
        for (String word : path.split("[_.-]")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.isEmpty() ? path : out.toString();
    }

    public static final String STRUCTURE_SUFFIX = "_house";

    /// The stall the mod would draw for this profession, as structure block nbt with the jigsaw blocks
    /// already right, to load into a structure block and build over.
    ///
    /// @return empty for an override of a profession without a job site
    public static Optional<NbtCompound> plot(ProfessionDefinition definition) {
        return plotBlock(definition).map(block -> PlotGenerator.plot(block, "plains"));
    }

    /// The block under the roof: first workstation, else first added workstation, else the overridden
    /// job site's first block.
    public static Optional<Identifier> plotBlock(ProfessionDefinition definition) {
        if (!definition.workstations().isEmpty()) {
            return Optional.of(definition.workstations().get(0));
        }
        if (!definition.addWorkstations().isEmpty()) {
            return Optional.of(definition.addWorkstations().get(0));
        }
        return Registries.VILLAGER_PROFESSION.getOrEmpty(definition.target())
                .flatMap(ProfessionLoader::jobSiteOf)
                .flatMap(poi -> poi.value().blockStates().stream().findFirst())
                .map(state -> Registries.BLOCK.getId(state.getBlock()));
    }

    public static String structureFile(ProfessionDefinition definition) {
        return definition.name() + ".nbt";
    }

    /// The structure json for the nbt, weight 2 like a mid-sized vanilla house. Suffixed because the
    /// structure id is its file name and would otherwise collide in reading with the profession id.
    public static String structureJson(ProfessionDefinition definition) {
        return """
                {
                  "_comment": "A village building for %s. The nbt beside this file is the stall the mod would draw itself; load it into a structure block, build your own house over it, keep the two jigsaw blocks, save it back under the same name.",
                  "structure": "%s",
                  "weight": 2
                }
                """.formatted(definition.target(), structureFile(definition));
    }

    /// Pack format from the running game, so the pack is never out of date.
    public static String datapackMeta(ProfessionDefinition definition) {
        return packMeta(packVersion(ResourceType.SERVER_DATA), "Trades and gift for " + definition.target());
    }

    public static String resourcepackMeta(ProfessionDefinition definition) {
        return packMeta(packVersion(ResourceType.CLIENT_RESOURCES), "Name for " + definition.target());
    }

    /// For the world datapack the editor's trades button writes into, one file per profession.
    public static String tradesPackMeta() {
        return packMeta(packVersion(ResourceType.SERVER_DATA),
                "VillagerTradingPlus trade files started from /ddv edit");
    }

    /// The two pack format constants next to this are deprecated; the version object is not.
    private static int packVersion(ResourceType type) {
        return SharedConstants.getGameVersion().getResourceVersion(type);
    }

    private static String packMeta(int format, String description) {
        return """
                {
                  "pack": {
                    "pack_format": %d,
                    "description": "%s"
                  }
                }
                """.formatted(format, description);
    }
}
