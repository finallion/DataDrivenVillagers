package com.lion.datadrivenvillagers.command;

import com.lion.datadrivenvillagers.profession.ProfessionDefinition;
import com.lion.datadrivenvillagers.profession.ProfessionLoader;
import com.lion.datadrivenvillagers.profession.ProfessionRegistry;
import com.lion.datadrivenvillagers.structure.StructureDefinition;
import com.lion.datadrivenvillagers.structure.StructureRegistry;
import com.lion.datadrivenvillagers.type.TypeDefinition;
import com.lion.datadrivenvillagers.type.TypeRegistry;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.VillagerProfession;

import java.util.List;

/// Answers the two questions a pack author actually has: what loaded, and why did my file not.
///
/// The tree itself lives here; every branch worth more than a few lines has a class of its own, and
/// this one keeps `list` and `errors` because they are one loop over three registries each.
public final class DataDrivenVillagersCommand {

    private DataDrivenVillagersCommand() { }

    private static final List<String[]> HELP = List.of(
            new String[] {"/ddv list", "what is loaded: professions, villager types, buildings"},
            new String[] {"/ddv errors", "which files were rejected, and why"},
            new String[] {"/ddv why <name>", "the chain behind one profession, type or building, and where it breaks"},
            new String[] {"/ddv why villager", "the same for the villager next to you: job, station, bed, plan, what it does"},
            new String[] {"/ddv blocks [text]", "which blocks are still free to be a workstation"},
            new String[] {"/ddv reload", "reads the folder again, and says what needs a restart instead"},
            new String[] {"/ddv edit [name]", "a screen for one profession file, saved back into the folder"},
            new String[] {"/ddv scaffold <name>", "writes the trades, gift, language and house files to edit"},
            new String[] {"/ddv export <name>", "zips a profession with everything it needs, to hand to other players"},
            new String[] {"/ddv doctor", "every report into doctor.txt, to paste into an issue"},
            new String[] {"/ddv help", "this list"});

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess access) {
        dispatcher.register(CommandManager.literal("ddv")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(Framed.framed(DataDrivenVillagersCommand::help))
                .then(CommandManager.literal("help").executes(Framed.framed(DataDrivenVillagersCommand::help)))
                .then(CommandManager.literal("list").executes(Framed.framed(DataDrivenVillagersCommand::list)))
                .then(CommandManager.literal("errors").executes(Framed.framed(DataDrivenVillagersCommand::errors)))
                .then(WhyCommand.node())
                .then(BlocksCommand.node())
                .then(ReloadCommand.node())
                .then(ScaffoldCommand.node())
                .then(ExportCommand.node())
                .then(DoctorCommand.node())
                .then(EditCommand.node()));
    }

    private static int help(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        for (String[] line : HELP) {
            source.sendFeedback(() -> Text.literal(line[0]).formatted(Formatting.WHITE)
                    .append(Text.literal("  " + line[1]).formatted(Formatting.GRAY)), false);
        }
        return HELP.size();
    }

    private static int list(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        if (ProfessionRegistry.isEmpty() && TypeRegistry.isEmpty() && StructureRegistry.isEmpty()) {
            source.sendFeedback(() -> Text.literal("Nothing loaded. Files go into ")
                    .append(Text.literal(ProfessionLoader.directory().getParent().toString())
                            .formatted(Formatting.YELLOW)), false);
            return 0;
        }

        for (ProfessionDefinition definition : ProfessionRegistry.ordered()) {
            source.sendFeedback(() -> Text.literal(definition.id().toString()).formatted(Formatting.GREEN)
                    .append(Text.literal("  station: " + definition.workstations()).formatted(Formatting.GRAY))
                    .append(Text.literal("  texture: " + textureSource(definition)).formatted(Formatting.GRAY))
                    .append(scheduleNote(definition))
                    .append(tradeStatus(definition)), false);
        }

        for (TypeDefinition definition : TypeRegistry.ordered()) {
            source.sendFeedback(() -> Text.literal(definition.id().toString()).formatted(Formatting.AQUA)
                    .append(Text.literal("  villager type  biomes: " + definition.biomes().size() + " named, " + definition.biomeTags().size() + " tag(s)")
                            .formatted(Formatting.GRAY)), false);
        }
        for (StructureDefinition definition : StructureRegistry.ordered()) {
            source.sendFeedback(() -> Text.literal(definition.id().toString()).formatted(Formatting.GOLD)
                    .append(Text.literal("  " + (definition.generated()
                                    ? "plot around " + definition.workstation().get()
                                    : "structure  " + definition.file().get())
                                    + "  weight " + definition.weight()
                                    + "  " + definition.targetPools().size() + " pool(s)")
                            .formatted(Formatting.GRAY)), false);
        }

        return ProfessionRegistry.ordered().size() + TypeRegistry.ordered().size()
                + StructureRegistry.ordered().size();
    }

    /// Only shown when a file asked for one, because "vanilla plan" is what every villager that ever
    /// existed has and printing it on every line would say nothing.
    private static Text scheduleNote(ProfessionDefinition definition) {
        return definition.schedule()
                .map(plan -> Text.literal("  schedule: " + plan.name()).formatted(Formatting.GRAY))
                .orElse(Text.empty());
    }

    private static String textureSource(ProfessionDefinition definition) {
        if (definition.texture().isPresent()) {
            return definition.texture().get().toString();
        }
        return definition.textureFile().orElse("vanilla lookup");
    }

    /// Read straight from the vanilla trade map, so this reports the truth no matter whether the
    /// trades came from VillagerTradingPlus, another mod or nowhere. Keeps this mod free of any
    /// build time dependency on VTP. Shared with {@link WhyCommand}, which reports the same line.
    static Text tradeStatus(ProfessionDefinition definition) {
        RegistryKey<VillagerProfession> key = RegistryKey.of(RegistryKeys.VILLAGER_PROFESSION, definition.id());
        Int2ObjectMap<TradeOffers.Factory[]> trades = TradeOffers.PROFESSION_TO_LEVELED_TRADE.get(key);
        if (trades == null || trades.isEmpty()) {
            return Text.literal("  no trades").formatted(Formatting.RED);
        }
        int total = trades.values().stream().mapToInt(factories -> factories.length).sum();
        return Text.literal("  " + total + " trade(s) over " + trades.size() + " tier(s)").formatted(Formatting.AQUA);
    }

    /// All three folders, because the reader of this command has one config folder and does not think
    /// of it as three. Reporting only professions here quietly hid every rejected villager type.
    private static int errors(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();

        int shown = 0;
        for (ProfessionRegistry.LoadError error : ProfessionRegistry.errors()) {
            shown += report(source, "professions", error.file(), error.reason());
        }
        for (TypeRegistry.LoadError error : TypeRegistry.errors()) {
            shown += report(source, "types", error.file(), error.reason());
        }
        for (StructureRegistry.LoadError error : StructureRegistry.errors()) {
            shown += report(source, "structures", error.file(), error.reason());
        }

        if (shown == 0) {
            source.sendFeedback(() -> Text.literal("No files were rejected.").formatted(Formatting.GREEN), false);
        }
        return shown;
    }

    private static int report(ServerCommandSource source, String folder, String file, String reason) {
        source.sendFeedback(() -> Text.literal(folder + "/").formatted(Formatting.DARK_GRAY)
                .append(Text.literal(file).formatted(Formatting.RED))
                .append(Text.literal("  " + reason).formatted(Formatting.GRAY)), false);
        return 1;
    }
}
