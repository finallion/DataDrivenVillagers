package com.lion.datadrivenvillagers.command;

import com.lion.datadrivenvillagers.ConfigFiles;
import com.lion.datadrivenvillagers.DefinitionParseException;
import com.lion.datadrivenvillagers.ReloadOutcome;
import com.lion.datadrivenvillagers.network.EditorOpenPayload;
import com.lion.datadrivenvillagers.network.EditorResultPayload;
import com.lion.datadrivenvillagers.network.EditorResultPayload.Note;
import com.lion.datadrivenvillagers.network.EditorSavePayload;
import com.lion.datadrivenvillagers.network.EditorTradesPayload;
import com.lion.datadrivenvillagers.network.LookSync;
import com.lion.datadrivenvillagers.platform.Network;
import com.lion.datadrivenvillagers.platform.PlatformInfo;
import com.lion.datadrivenvillagers.profession.ProfessionDefinition;
import com.lion.datadrivenvillagers.profession.ProfessionLoader;
import com.lion.datadrivenvillagers.profession.ProfessionParser;
import com.lion.datadrivenvillagers.profession.ProfessionRegistry;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import net.minecraft.command.CommandSource;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.VillagerProfession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/// Opens the editor, and takes back what it sends.
///
/// The command half only opens what an author could edit by hand; the editor knows the field names
/// and allowed values, saving a reload to find out. Both halves live here so what open sends and
/// what save expects cannot drift apart.
public final class EditCommand {

    private static final String EXTENSION = ".json";

    /// A profession id is the lower-cased file name; a case sensitive disk could then save two files under one id.
    private static final Pattern FILE_NAME = Pattern.compile("[a-z0-9_-]{1,64}");

    /// Deliberately without a workstation.
    private static final String TEMPLATE = """
            {
              "_comment": "Made with /ddv edit. Only the workstation is required - pick one from the list under the box, it holds the blocks no other job site has claimed."
            }
            """;

    /// The files in the folder, not the professions that loaded.
    private static final SuggestionProvider<ServerCommandSource> FILES =
            (context, builder) -> CommandSource.suggestMatching(names(), builder);

    private EditCommand() {
    }

    public static LiteralArgumentBuilder<ServerCommandSource> node() {
        return CommandManager.literal("edit")
                .executes(context -> open(context, ""))
                .then(CommandManager.argument("profession", StringArgumentType.word())
                        .suggests(FILES)
                        .executes(context -> open(context, StringArgumentType.getString(context, "profession"))));
    }

    private static List<String> names() {
        try (Stream<Path> stream = Files.list(ProfessionLoader.directory())) {
            return stream.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(EXTENSION))
                    .map(name -> name.substring(0, name.length() - EXTENSION.length()))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /// An unknown name is not an error here: editing a profession with no file yet is how a new one starts.
    private static int open(CommandContext<ServerCommandSource> context, String typed) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("The editor is a screen, so it needs a player to open it on. "
                    + "From a console, edit the file in " + ProfessionLoader.directory() + " instead."));
            return 0;
        }

        String name = typed.toLowerCase(Locale.ROOT);
        if (!name.isEmpty() && !FILE_NAME.matcher(name).matches()) {
            source.sendError(Text.literal("That cannot be a file name. Lower case letters, digits, "
                    + "underscore and dash only."));
            return 0;
        }

        String json = TEMPLATE;
        Optional<Path> existing = name.isEmpty() ? Optional.empty() : find(name);
        if (existing.isPresent()) {
            try {
                json = Files.readString(existing.get(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                source.sendError(Text.literal("Could not read " + existing.get().getFileName() + ": "
                        + e.getMessage()));
                return 0;
            }
        }

        Network.send(player, new EditorOpenPayload(name, json, state(name, existing.isPresent())));
        return 1;
    }

    /// On disk, valid, but not yet a profession: registration happens only at startup.
    private static Note state(String name, boolean onDisk) {
        if (!onDisk) {
            return EditorResultPayload.warn("New file. It exists nowhere until you save, and the "
                    + "profession itself only after the next restart.");
        }
        Identifier id = Identifier.of("datadrivenvillagers", name);
        if (ProfessionRegistry.get(id).isPresent()) {
            return EditorResultPayload.ok("This profession is loaded. Changes to the fields marked "
                    + "with * still need a restart.");
        }
        return ProfessionRegistry.errors().stream()
                .filter(error -> error.file().equalsIgnoreCase(name + EXTENSION))
                .findFirst()
                .map(error -> EditorResultPayload.bad("Rejected: " + error.reason()))
                .orElseGet(() -> EditorResultPayload.warn("Saved on disk, but NOT in the game: a new "
                        + "profession is only created while the game starts. Restart to bring it in."));
    }

    /// A file saved before names were held to lower case still opens and saves under its own spelling.
    private static Optional<Path> find(String name) {
        Path exact = ProfessionLoader.directory().resolve(name + EXTENSION);
        if (Files.isRegularFile(exact)) {
            return Optional.of(exact);
        }
        try (Stream<Path> stream = Files.list(ProfessionLoader.directory())) {
            return stream.filter(path -> path.getFileName().toString()
                    .equalsIgnoreCase(name + EXTENSION)).findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /// Parses before writing, so a file the loader would reject never replaces a working profession on disk.
    public static void save(ServerPlayerEntity player, EditorSavePayload payload) {
        if (!player.hasPermissionLevel(2)) {
            reply(player, false, List.of(EditorResultPayload.bad(
                    "You are not allowed to edit professions on this server.")));
            return;
        }

        String name = payload.fileName().toLowerCase(Locale.ROOT);
        if (!FILE_NAME.matcher(name).matches()) {
            reply(player, false, List.of(EditorResultPayload.bad("That cannot be a file name. Lower case "
                    + "letters, digits, underscore and dash only.")));
            return;
        }

        Path folder = ProfessionLoader.directory().toAbsolutePath().normalize();
        // An existing file keeps its on-disk spelling; saving never leaves a second copy on a case sensitive disk.
        Path file = find(name).map(found -> found.toAbsolutePath().normalize())
                .orElseGet(() -> folder.resolve(name + EXTENSION).normalize());
        if (!file.getParent().equals(folder)) {
            reply(player, false, List.of(EditorResultPayload.bad(
                    "That name does not stay inside the professions folder.")));
            return;
        }

        JsonObject root;
        try {
            root = JsonParser.parseString(payload.json()).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            reply(player, false, List.of(EditorResultPayload.bad("Not readable as json: " + e.getMessage())));
            return;
        }

        ProfessionDefinition definition;
        try {
            definition = ProfessionParser.parse(name, root);
        } catch (DefinitionParseException e) {
            reply(player, false, List.of(EditorResultPayload.bad(e.getMessage()),
                    EditorResultPayload.bad("Nothing was written, the file on disk is unchanged.")));
            return;
        }

        // The parser skips blocks; the loader must reject before the write, or a bad file fails at start.
        Optional<String> blocks = ProfessionLoader.workstationRejection(definition);
        if (blocks.isPresent()) {
            reply(player, false, List.of(EditorResultPayload.bad(blocks.get()),
                    EditorResultPayload.bad("Nothing was written, the file on disk is unchanged.")));
            return;
        }

        boolean existed = Files.isRegularFile(file);
        try {
            Files.createDirectories(folder);
            // Through a temporary file, so a crash mid-write cannot leave a json that fails at the next start.
            ConfigFiles.writeAtomically(file, payload.json());
        } catch (IOException e) {
            reply(player, false, List.of(EditorResultPayload.bad(
                    "Could not write " + file.getFileName() + ": " + e.getMessage())));
            return;
        }

        List<Note> notes = reload(player.getServer(), name, file.getFileName().toString(), existed);
        tradeCount(definition, notes);
        // Success is judged by the reload, not by the write: a save the reload rejects did not go well.
        reply(player, notes.stream().noneMatch(note -> note.level() == EditorResultPayload.Level.BAD), notes);
    }

    /// The same truth `/ddv list` prints, put where the author already is.
    private static void tradeCount(ProfessionDefinition definition, List<Note> notes) {
        int total = knownTrades(definition);
        notes.add(total == 0
                ? EditorResultPayload.warn("No trades - the Trades button (Work page) starts a file.")
                : EditorResultPayload.ok("The game holds " + total + " trade(s) for "
                        + definition.target() + "."));
    }

    private static int knownTrades(ProfessionDefinition definition) {
        RegistryKey<VillagerProfession> key =
                RegistryKey.of(RegistryKeys.VILLAGER_PROFESSION, definition.target());
        Int2ObjectMap<TradeOffers.Factory[]> trades = TradeOffers.PROFESSION_TO_LEVELED_TRADE.get(key);
        return trades == null ? 0 : trades.values().stream().mapToInt(factories -> factories.length).sum();
    }

    /// The world datapack the trades button writes into; one pack shared by every profession, not one per use.
    private static final String TRADES_PACK = "ddv_trades";

    /// Writes the VillagerTradingPlus starting file for this profession into the world's own datapacks.
    public static void trades(ServerPlayerEntity player, EditorTradesPayload payload) {
        if (!player.hasPermissionLevel(2)) {
            reply(player, false, List.of(EditorResultPayload.bad(
                    "You are not allowed to edit professions on this server.")));
            return;
        }

        String name = payload.fileName().toLowerCase(Locale.ROOT);
        if (!FILE_NAME.matcher(name).matches()) {
            reply(player, false, List.of(EditorResultPayload.bad("That cannot be a file name. Lower case "
                    + "letters, digits, underscore and dash only.")));
            return;
        }

        Optional<Path> file = find(name);
        if (file.isEmpty()) {
            reply(player, false, List.of(EditorResultPayload.bad("There is no " + name + EXTENSION
                    + " yet. Save the profession first - the trades are filled in from what it says.")));
            return;
        }

        // Built from the file on disk, not the packet, so the trades name the same profession the parser would.
        ProfessionDefinition definition;
        try {
            definition = ProfessionParser.parse(name, JsonParser.parseString(
                    Files.readString(file.get(), StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (IOException | RuntimeException e) {
            reply(player, false, List.of(
                    EditorResultPayload.bad("Cannot build trades from " + file.get().getFileName() + ": "
                            + e.getMessage()),
                    EditorResultPayload.bad("Fix the profession and save it, then try again.")));
            return;
        }

        MinecraftServer server = player.getServer();
        Path datapacks = server.getSavePath(WorldSavePath.DATAPACKS).toAbsolutePath().normalize();
        Scaffold.Piece piece = Scaffold.tradesPiece(definition);
        Path pack = datapacks.resolve(TRADES_PACK);
        // The piece knows its place inside a datapack; the pack folder stands in for "datapack/".
        Path target = pack.resolve(piece.destination().substring("datapack/".length())).normalize();
        if (!target.startsWith(datapacks)) {
            // Redundant with the pattern above; guards against that pattern being loosened later.
            reply(player, false, List.of(EditorResultPayload.bad(
                    "That name does not stay inside the world's datapacks folder.")));
            return;
        }

        if (Files.isRegularFile(target)) {
            List<Note> notes = new ArrayList<>();
            notes.add(EditorResultPayload.warn("A trades file for " + name + " is already in datapacks/"
                    + TRADES_PACK + " - nothing overwritten."));
            tradesNotes(definition, notes);
            reply(player, true, notes);
            return;
        }

        try {
            Files.createDirectories(target.getParent());
            Path meta = pack.resolve("pack.mcmeta");
            if (!Files.isRegularFile(meta)) {
                ConfigFiles.writeAtomically(meta, Scaffold.tradesPackMeta());
            }
            ConfigFiles.writeAtomically(target, piece.content());
        } catch (IOException e) {
            reply(player, false, List.of(EditorResultPayload.bad(
                    "Could not write into the world's datapacks folder: " + e.getMessage())));
            return;
        }

        List<Note> notes = new ArrayList<>();
        notes.add(EditorResultPayload.ok("Wrote " + name + ".json into this world's datapacks/"
                + TRADES_PACK + "."));
        notes.add(EditorResultPayload.warn("Now type /reload in the chat."));
        tradesNotes(definition, notes);
        reply(player, true, notes);
    }

    private static void tradesNotes(ProfessionDefinition definition, List<Note> notes) {
        if (!PlatformInfo.isLoaded("villagertradingplus")) {
            notes.add(EditorResultPayload.warn(
                    "VillagerTradingPlus is not installed - nothing reads this file yet."));
        }
        int total = knownTrades(definition);
        if (total > 0) {
            notes.add(EditorResultPayload.warn("The game already knows " + total + " trade(s) for "
                    + definition.target() + "; this file replaces them when read."));
        }
    }

    private static List<Note> reload(MinecraftServer server, String name, String fileName, boolean existed) {
        List<Note> notes = new ArrayList<>();
        notes.add(EditorResultPayload.ok((existed ? "Saved " : "Created ") + fileName));

        if (server == null) {
            return notes;
        }

        boolean spoken = false;
        boolean saidRestart = false;
        for (ReloadOutcome outcome : ProfessionLoader.reload(server)) {
            if (!outcome.file().equalsIgnoreCase(name + EXTENSION)) {
                // Every file reloads, but this screen reports on one; a wall of "unchanged" lines would bury it.
                continue;
            }
            spoken = true;
            saidRestart |= outcome.kind() == ReloadOutcome.Kind.RESTART;
            notes.add(switch (outcome.kind()) {
                case UPDATED -> EditorResultPayload.ok("Applied to the running game: " + outcome.detail());
                case UNCHANGED -> EditorResultPayload.ok("Nothing in it had changed.");
                case RESTART -> EditorResultPayload.warn("NOT in the game yet: " + outcome.detail());
                case REJECTED -> EditorResultPayload.bad("Rejected when read back: " + outcome.detail());
            });
        }

        // Silence is not success: a file the reload said nothing about changed nothing the running game can see.
        if (!spoken) {
            notes.add(EditorResultPayload.warn("The reload had nothing to say about this file."));
        }

        // Asked of the registry only when the outcome has not said it, to avoid a second restart line on screen.
        if (!saidRestart && ProfessionRegistry.get(Identifier.of("datadrivenvillagers", name)).isEmpty()) {
            notes.add(EditorResultPayload.warn("Restart the game to create this profession."));
        }

        // Broadcast without a chat line; it was the least important note on a screen short of room.
        LookSync.broadcast(server);
        return notes;
    }

    private static void reply(ServerPlayerEntity player, boolean ok, List<Note> notes) {
        Network.send(player, new EditorResultPayload(ok, notes));
        if (!ok) {
            // Also sent to chat so the reason survives closing the screen; the first red line, not the first line.
            Note reason = notes.stream().filter(note -> note.level() == EditorResultPayload.Level.BAD)
                    .findFirst().orElse(notes.get(0));
            player.sendMessage(Text.literal(reason.text()).formatted(Formatting.RED), false);
        }
    }
}
