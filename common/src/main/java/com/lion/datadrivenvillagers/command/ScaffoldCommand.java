package com.lion.datadrivenvillagers.command;

import com.lion.datadrivenvillagers.ConfigFiles;
import com.lion.datadrivenvillagers.DataDrivenVillagers;
import com.lion.datadrivenvillagers.profession.ProfessionDefinition;
import com.lion.datadrivenvillagers.profession.ProfessionRegistry;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.command.CommandSource;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/// `/ddv scaffold <name>`: writes trades, gift, language file and a village stall into
/// `professions/scaffold/<name>/`, with a readme naming each file's place in a pack. Existing files
/// are never overwritten; kept and written files are both reported.
public final class ScaffoldCommand {

    private static final String README = "README.txt";
    private static final String STRUCTURES = "config/" + DataDrivenVillagers.MOD_ID + "/structures/";

    private static final SuggestionProvider<ServerCommandSource> LOADED =
            (context, builder) -> CommandSource.suggestMatching(
                    ProfessionRegistry.ordered().stream().map(ProfessionDefinition::name).toList(), builder);

    private ScaffoldCommand() {
    }

    public static LiteralArgumentBuilder<ServerCommandSource> node() {
        return CommandManager.literal("scaffold")
                .then(CommandManager.argument("profession", StringArgumentType.word())
                        .suggests(LOADED)
                        .executes(Framed.framed(ScaffoldCommand::scaffold)));
    }

    private static int scaffold(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String name = StringArgumentType.getString(context, "profession");

        Optional<ProfessionDefinition> found = find(name);
        if (found.isEmpty()) {
            source.sendError(Text.literal("No profession called \"" + name + "\" is loaded. /ddv list "
                    + "shows what is, /ddv errors which files were rejected."));
            return 0;
        }
        ProfessionDefinition definition = found.get();

        Path folder = Scaffold.folderFor(definition);
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            source.sendError(Text.literal("Could not create " + folder + ": " + e.getMessage()));
            return 0;
        }

        int written = 0;
        for (Scaffold.Piece piece : Scaffold.pieces(definition)) {
            Path target = folder.resolve(piece.file());
            if (Files.exists(target)) {
                source.sendFeedback(() -> Text.literal("kept  ").formatted(Formatting.DARK_GRAY)
                        .append(Text.literal(piece.file()).formatted(Formatting.WHITE))
                        .append(Text.literal("  yours, left alone").formatted(Formatting.GRAY)), false);
                continue;
            }
            if (!write(source, target, piece.content())) {
                return written;
            }
            written++;
            source.sendFeedback(() -> Text.literal("wrote ").formatted(Formatting.GREEN)
                    .append(Text.literal(piece.file()).formatted(Formatting.WHITE))
                    .append(Text.literal("  -> " + piece.destination()).formatted(Formatting.GRAY)), false);
        }

        written += building(source, definition, folder);

        // The readme is always rewritten; it is not meant to be edited.
        if (!write(source, folder.resolve(README), readme(definition))) {
            return written;
        }

        source.sendFeedback(() -> Text.literal(folder.toString()).formatted(Formatting.YELLOW)
                .append(Text.literal("  edit these, then /ddv export " + definition.name()
                        + " puts them into one zip.").formatted(Formatting.GRAY)), false);
        return written;
    }

    /// The stall as nbt plus the json that places it. Binary, so not a {@link Scaffold.Piece}; same
    /// rule: never overwritten, reported either way.
    private static int building(ServerCommandSource source, ProfessionDefinition definition, Path folder) {
        Optional<NbtCompound> plot = Scaffold.plot(definition);
        if (plot.isEmpty()) {
            source.sendFeedback(() -> Text.literal("none  ").formatted(Formatting.DARK_GRAY)
                    .append(Text.literal(Scaffold.structureFile(definition)).formatted(Formatting.WHITE))
                    .append(Text.literal("  " + definition.target() + " has no job site, so there is no "
                            + "block to build a stall around").formatted(Formatting.GRAY)), false);
            return 0;
        }

        int written = 0;
        Path nbt = folder.resolve(Scaffold.structureFile(definition));
        if (Files.exists(nbt)) {
            kept(source, nbt);
        } else {
            try {
                NbtIo.writeCompressed(plot.get(), nbt);
            } catch (IOException e) {
                source.sendError(Text.literal("Could not write " + nbt + ": " + e.getMessage()));
                return written;
            }
            written++;
            wrote(source, nbt, STRUCTURES + Scaffold.structureFile(definition));
        }

        Path json = folder.resolve(definition.name() + Scaffold.STRUCTURE_SUFFIX + ".json");
        if (Files.exists(json)) {
            kept(source, json);
        } else if (write(source, json, Scaffold.structureJson(definition))) {
            written++;
            wrote(source, json, STRUCTURES + json.getFileName());
        }
        return written;
    }

    private static void kept(ServerCommandSource source, Path file) {
        source.sendFeedback(() -> Text.literal("kept  ").formatted(Formatting.DARK_GRAY)
                .append(Text.literal(file.getFileName().toString()).formatted(Formatting.WHITE))
                .append(Text.literal("  yours, left alone").formatted(Formatting.GRAY)), false);
    }

    private static void wrote(ServerCommandSource source, Path file, String destination) {
        source.sendFeedback(() -> Text.literal("wrote ").formatted(Formatting.GREEN)
                .append(Text.literal(file.getFileName().toString()).formatted(Formatting.WHITE))
                .append(Text.literal("  -> " + destination).formatted(Formatting.GRAY)), false);
    }

    /// By file name first, then by target id: an override is registered under the vanilla id, and its
    /// file name is what `/ddv list` shows.
    static Optional<ProfessionDefinition> find(String name) {
        List<ProfessionDefinition> loaded = ProfessionRegistry.ordered();
        return loaded.stream().filter(definition -> definition.name().equalsIgnoreCase(name)).findFirst()
                .or(() -> loaded.stream()
                        .filter(definition -> definition.target().toString().equalsIgnoreCase(name)
                                || definition.target().getPath().equalsIgnoreCase(name))
                        .findFirst());
    }

    private static boolean write(ServerCommandSource source, Path target, String content) {
        try {
            ConfigFiles.writeAtomically(target, content);
            return true;
        } catch (IOException e) {
            source.sendError(Text.literal("Could not write " + target + ": " + e.getMessage()));
            return false;
        }
    }

    private static String readme(ProfessionDefinition definition) {
        StringBuilder out = new StringBuilder();
        out.append("Scaffold for ").append(definition.target()).append("\n");
        out.append("Written by /ddv scaffold ").append(definition.name())
                .append(", and rewritten on every run. Edit the json files, not this one.\n\n");
        out.append("The mod reads none of these three. They are the parts that belong to a datapack, a\n");
        out.append("resource pack and to VillagerTradingPlus, and each of them is a format you would\n");
        out.append("otherwise have to look up somewhere else first.\n\n");

        for (Scaffold.Piece piece : Scaffold.pieces(definition)) {
            out.append(piece.file()).append("\n    -> ").append(piece.destination()).append("\n");
        }

        out.append("\nThe two folder names above say which pack each file belongs to. /ddv export ")
                .append(definition.name()).append("\nbuilds both packs and this profession into a single "
                        + "zip, using whatever is in this folder.\n");

        Scaffold.plotBlock(definition).ifPresent(block -> {
            String nbt = Scaffold.structureFile(definition);
            String json = definition.name() + Scaffold.STRUCTURE_SUFFIX + ".json";
            out.append("\n").append(nbt).append(" and ").append(json).append("\n    -> ")
                    .append(STRUCTURES).append("\n\n");
            out.append("A village building: the stall the mod would draw around ").append(block)
                    .append(",\nsaved the way a structure block saves. Copy both into the structures folder "
                            + "and it turns\nup in villages as it is. To build something better, copy the nbt "
                            + "into\n<world>/generated/minecraft/structures/, load it in a structure block "
                            + "under the name\nminecraft:").append(definition.name())
                    .append(", build over it, keep the two jigsaw blocks where they are, save it, and\n"
                            + "copy the result back over the nbt. The jigsaw blocks are the part that "
                            + "fails silently\nwhen done from scratch; here they are already right.\n");
        });

        if (definition.gift().isEmpty()) {
            out.append("\nThis profession has no \"gift\" field yet, so the gift table above would never be\n");
            out.append("found. Add this line to ").append(definition.name()).append(".json:\n\n");
            out.append("    \"gift\": \"").append(Scaffold.giftId(definition)).append("\"\n");
        }
        return out.toString();
    }
}
