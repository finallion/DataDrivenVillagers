package com.lion.datadrivenvillagers.command;

import com.lion.datadrivenvillagers.ConfigFiles;
import com.lion.datadrivenvillagers.DataDrivenVillagers;
import com.lion.datadrivenvillagers.profession.ProfessionDefinition;
import com.lion.datadrivenvillagers.profession.ProfessionLoader;
import com.lion.datadrivenvillagers.profession.ProfessionRegistry;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/// `/ddv export <name>`: one profession as a zip with three top level folders, `config/`, `datapack/`
/// and `resourcepack/`, named after where their contents go. Trades, gift and name come from the
/// scaffold folder or are generated; a datapack written elsewhere is invisible here, so generated
/// files are marked as made up in the report and in the readme inside the zip.
public final class ExportCommand {

    private static final String FOLDER = "export";
    private static final String CONFIG = "config/" + DataDrivenVillagers.MOD_ID + "/professions/";
    private static final String README = "README.txt";

    private static final SuggestionProvider<ServerCommandSource> LOADED =
            (context, builder) -> CommandSource.suggestMatching(
                    ProfessionRegistry.ordered().stream().map(ProfessionDefinition::name).toList(), builder);

    private ExportCommand() {
    }

    public static LiteralArgumentBuilder<ServerCommandSource> node() {
        return CommandManager.literal(FOLDER)
                .then(CommandManager.argument("profession", StringArgumentType.word())
                        .suggests(LOADED)
                        .executes(Framed.framed(ExportCommand::export)));
    }

    /// One entry of the zip, listed in the chat report and in the readme inside the zip.
    ///
    /// @param real false for a file that was made up here, or for one that could not be included
    private record Included(String path, boolean real, String note) {
    }

    private static int export(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String name = StringArgumentType.getString(context, "profession");

        Optional<ProfessionDefinition> found = ScaffoldCommand.find(name);
        if (found.isEmpty()) {
            source.sendError(Text.literal("No profession called \"" + name + "\" is loaded. /ddv list "
                    + "shows what is, /ddv errors which files were rejected."));
            return 0;
        }

        ProfessionDefinition definition = found.get();
        Path professions = ProfessionLoader.directory();
        Path json = professions.resolve(definition.name() + ".json");
        if (!Files.isRegularFile(json)) {
            // Loaded but deleted from disk since.
            source.sendError(Text.literal(json + " is gone, so there is nothing to export. The "
                    + "profession stays loaded until the game restarts."));
            return 0;
        }

        Path folder = professions.getParent().resolve(FOLDER);
        Path zip = folder.resolve(definition.name() + ".zip");
        List<Included> included = new ArrayList<>();

        try {
            Files.createDirectories(folder);
            // Overwritten: a zip is built from the parts, never edited in place.
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip), StandardCharsets.UTF_8)) {
                profession(out, definition, json, included);
                texture(out, definition, professions, included);

                write(out, "datapack/pack.mcmeta", Scaffold.datapackMeta(definition));
                write(out, "resourcepack/pack.mcmeta", Scaffold.resourcepackMeta(definition));
                scaffolded(out, definition, included);

                write(out, README, readme(definition, included));
            }
        } catch (IOException e) {
            source.sendError(Text.literal("Could not write " + zip + ": " + e.getMessage()));
            return 0;
        }

        return report(source, definition, zip, included);
    }

    private static void profession(ZipOutputStream out, ProfessionDefinition definition, Path json,
                                   List<Included> included) throws IOException {
        String path = CONFIG + json.getFileName();
        copy(out, path, json);
        included.add(new Included(path, true, "the profession itself, exactly as it is on disk"));
    }

    /// A png next to the json travels with the zip; a resource pack texture and a missing texture are
    /// only reported.
    private static void texture(ZipOutputStream out, ProfessionDefinition definition, Path professions,
                                List<Included> included) throws IOException {
        Optional<String> file = definition.textureFile();
        if (file.isPresent()) {
            // Through ConfigFiles, so no zip ever carries a file from outside the folder.
            Optional<Path> png = ConfigFiles.resolveInside(professions, file.get())
                    .filter(Files::isRegularFile);
            if (png.isPresent()) {
                copy(out, CONFIG + file.get(), png.get());
                included.add(new Included(CONFIG + file.get(), true, "the texture named in the json"));
            } else {
                included.add(new Included(CONFIG + file.get(), false,
                        "named in the json but not in the professions folder, so it is not in this zip"));
            }
            return;
        }

        if (definition.texture().isPresent()) {
            included.add(new Included("(no image)", false, "the json points at " + definition.texture().get()
                    + ", which lives in a resource pack this zip cannot reach"));
            return;
        }
        included.add(new Included("(no image)", false, "the json names no texture at all, so villagers of "
                + "this profession render with the missing texture"));
    }

    private static void scaffolded(ZipOutputStream out, ProfessionDefinition definition,
                                   List<Included> included) throws IOException {
        Path scaffold = Scaffold.folderFor(definition);
        for (Scaffold.Piece piece : Scaffold.pieces(definition)) {
            Path edited = scaffold.resolve(piece.file());
            boolean own = Files.isRegularFile(edited);
            write(out, piece.destination(), own
                    ? Files.readString(edited, StandardCharsets.UTF_8)
                    : piece.content());
            included.add(new Included(piece.destination(), own, own
                    ? "yours, from " + Scaffold.FOLDER + "/" + definition.name() + "/" + piece.file()
                    : "a starting point written just now, which nobody has looked at yet"));
        }
    }

    private static int report(ServerCommandSource source, ProfessionDefinition definition, Path zip,
                              List<Included> included) {
        for (Included entry : included) {
            source.sendFeedback(() -> Text.literal(entry.real() ? "[ok] " : "[??] ")
                    .formatted(entry.real() ? Formatting.GREEN : Formatting.YELLOW)
                    .append(Text.literal(entry.path()).formatted(Formatting.WHITE))
                    .append(Text.literal("  " + entry.note()).formatted(Formatting.GRAY)), false);
        }

        // From the live trade map, not from the zip: says whether this world uses the exported trades.
        source.sendFeedback(() -> Text.literal("in this world  ").formatted(Formatting.DARK_AQUA)
                .append(DataDrivenVillagersCommand.tradeStatus(definition)), false);

        source.sendFeedback(() -> Text.literal("Wrote ").formatted(Formatting.GREEN)
                .append(Text.literal(zip.toString()).formatted(Formatting.YELLOW)), false);

        long guessed = included.stream().filter(entry -> !entry.real()).count();
        if (guessed > 0) {
            source.sendFeedback(() -> Text.literal(guessed + " part(s) above are placeholders or missing. "
                            + "/ddv scaffold " + definition.name() + " writes them out to be edited, and "
                            + "the readme in the zip says the same thing to whoever unpacks it.")
                    .formatted(Formatting.YELLOW), false);
        }
        return included.size();
    }

    private static String readme(ProfessionDefinition definition, List<Included> included) {
        StringBuilder out = new StringBuilder();
        out.append(definition.target()).append("\n");
        out.append("A villager profession for DataDrivenVillagers, exported from a running game.\n\n");

        out.append("Where each folder goes\n");
        out.append("----------------------\n");
        out.append("config/         into your .minecraft/config folder, keeping the folders as they are.\n");
        out.append("                The profession is read from there, not from a datapack: professions\n");
        out.append("                are registered before Minecraft reads any pack, which is too late.\n");
        out.append("                Changing this needs a restart, not /reload.\n");
        out.append("datapack/       zip its contents, or drop the folder into <world>/datapacks/.\n");
        out.append("                Trades need VillagerTradingPlus installed. The gift is a plain loot\n");
        out.append("                table and needs nothing.\n");
        out.append("resourcepack/   zip its contents, or drop the folder into .minecraft/resourcepacks/.\n");
        out.append("                Only the name comes from here. The texture does not: it sits beside\n");
        out.append("                the json in config/ and needs no resource pack.\n\n");

        out.append("What is in this zip\n");
        out.append("-------------------\n");
        for (Included entry : included) {
            out.append(entry.real() ? "  [real] " : "  [made up] ")
                    .append(entry.path()).append("\n            ").append(entry.note()).append("\n");
        }

        out.append("\nAnything marked [made up] was generated by /ddv export because nothing better was\n");
        out.append("there to copy. It is valid and it will load, but it is a starting point rather than\n");
        out.append("what the author meant.\n\n");

        out.append("Check it worked\n");
        out.append("---------------\n");
        out.append("/ddv why ").append(definition.name())
                .append(" walks the whole chain and names the first link that is broken.\n");
        return out.toString();
    }

    private static void write(ZipOutputStream out, String path, String content) throws IOException {
        out.putNextEntry(new ZipEntry(path));
        out.write(content.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }

    private static void copy(ZipOutputStream out, String path, Path file) throws IOException {
        out.putNextEntry(new ZipEntry(path));
        Files.copy(file, out);
        out.closeEntry();
    }
}
