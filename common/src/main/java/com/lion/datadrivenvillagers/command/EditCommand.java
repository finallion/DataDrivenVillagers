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
/// The command half is small on purpose. Everything a pack author can do here he could do by writing
/// the file himself; the editor knows the field names and the allowed values, which is the part that
/// costs a reload to find out otherwise. Nothing new becomes possible, the loop just gets shorter.
///
/// Both halves live here because they are the same conversation: what goes out at open is what comes
/// back at save, and splitting them over two classes would let the two drift apart.
public final class EditCommand {

    private static final String EXTENSION = ".json";

    /// Lower case only. A profession id is built by lower-casing the file
    /// name, so `Test.json` becomes `datadrivenvillagers:test` - and then nothing lines up any more:
    /// the reload reports `test.json`, the completion offers `test`, and on a case sensitive file
    /// system a save under either spelling leaves two files that both want the same id. Holding the
    /// file name to what the id will be is the only spelling that stays true everywhere.
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

    /// An unknown name is not an error here, unlike everywhere else in this command tree: asking to
    /// edit a baker in a folder that has none is how a new one is started, and refusing it would make
    /// the author create an empty file by hand first.
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

        if (json.length() > EditorOpenPayload.MAX_JSON) {
            source.sendError(Text.literal(existing.map(path -> path.getFileName().toString()).orElse(name)
                    + " is " + json.length() + " characters and one packet carries "
                    + EditorOpenPayload.MAX_JSON + ". The editor cannot open it; a text editor can."));
            return 0;
        }

        Network.send(player, new EditorOpenPayload(name, json, state(name, existing.isPresent())));
        return 1;
    }

    /// What the running game holds for this file right now, asked before the author changes anything.
    ///
    /// Three states, and the middle one is the whole reason this exists: the file is on disk, it is
    /// not broken, and there is still no profession - because one is only registered while the game
    /// starts. Without this line an author edits a file for an hour and wonders why nothing he does
    /// reaches a villager.
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

    /// Looks past the spelling, because a file written before the name was held to lower case is still
    /// somebody's work. `Test.json` opens under `test` and is saved back under the name it already has.
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

    /// Runs on the server thread, with everything the client sent treated as a suggestion.
    ///
    /// Order matters: parse first, write second. A file the loader would reject never reaches the
    /// folder, so a failed save leaves the author exactly where he was instead of replacing a working
    /// profession with a typo.
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
        // An existing file keeps the spelling it has on disk, so saving never leaves a second copy next
        // to it on a file system that tells the two apart.
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

        // The parser knows nothing about blocks; the loader does, and until 2026-09-04 it was only
        // asked after the file was on disk. A workstation this game does not have, or one another
        // job site owns, is the rejection an author meets most, and it has to come before the write
        // like every other one - or the file lies in the folder and complains at every start.
        Optional<String> blocks = ProfessionLoader.workstationRejection(definition);
        if (blocks.isPresent()) {
            reply(player, false, List.of(EditorResultPayload.bad(blocks.get()),
                    EditorResultPayload.bad("Nothing was written, the file on disk is unchanged.")));
            return;
        }

        boolean existed = Files.isRegularFile(file);
        try {
            Files.createDirectories(folder);
            // Through a temporary file: a crash mid-write would otherwise leave half a json behind,
            // and the next start would reject what was a working profession.
            ConfigFiles.writeAtomically(file, payload.json());
        } catch (IOException e) {
            reply(player, false, List.of(EditorResultPayload.bad(
                    "Could not write " + file.getFileName() + ": " + e.getMessage())));
            return;
        }

        List<Note> notes = reload(player.getServer(), name, file.getFileName().toString(), existed);
        tradeCount(definition, notes);
        // Judged by the reload, not by "the file was written": a save the reload rejected is not a
        // save that went well, however true the green line on top of it is.
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

    /// The name of the world datapack the trades button writes into. One pack for all professions,
    /// so a world that used the button five times has one entry in `/datapack list`, not five.
    private static final String TRADES_PACK = "ddv_trades";

    /// Writes the VillagerTradingPlus starting file for this profession into the world's own
    /// datapacks.
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

        // Built from the file on disk, not from anything the packet carried: the trades name the
        // profession they are for, and that answer has to come from the same parser as everything else.
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
            // The pattern above already forbids everything that could step outside. The second lock is
            // for the day the pattern is loosened without anyone remembering what it guarded.
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
                // Every other file is read again as well, but this screen is about one of them, and a
                // wall of "unchanged" would bury the line the author is waiting for.
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

        // Silence used to read as success. It cannot: a file the reload said nothing about is a file
        // that changed nothing the running game can see.
        if (!spoken) {
            notes.add(EditorResultPayload.warn("The reload had nothing to say about this file."));
        }

        // The plainest form of the same news, asked of the registry rather than guessed from the
        // outcome - but only when the outcome has not already said it: two yellow restart lines in
        // one answer were half of what pushed the report over its room on the screen.
        if (!saidRestart && ProfessionRegistry.get(Identifier.of("datadrivenvillagers", name)).isEmpty()) {
            notes.add(EditorResultPayload.warn("Restart the game to create this profession."));
        }

        // Still broadcast, no longer narrated: the line about it was the least important one on a
        // screen that ran out of room for the most important.
        LookSync.broadcast(server);
        return notes;
    }

    private static void reply(ServerPlayerEntity player, boolean ok, List<Note> notes) {
        Network.send(player, new EditorResultPayload(ok, notes));
        if (!ok) {
            // Also in the chat, so the reason survives closing the screen. The first red line, which
            // is not always the first line: a rejected reload sits under a green "Saved".
            Note reason = notes.stream().filter(note -> note.level() == EditorResultPayload.Level.BAD)
                    .findFirst().orElse(notes.get(0));
            player.sendMessage(Text.literal(reason.text()).formatted(Formatting.RED), false);
        }
    }
}
