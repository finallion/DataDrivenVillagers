package com.lion.datadrivenvillagers.command;

import com.lion.datadrivenvillagers.platform.PlatformInfo;
import com.lion.datadrivenvillagers.profession.ProfessionDefinition;
import com.lion.datadrivenvillagers.profession.ProfessionLoader;
import com.lion.datadrivenvillagers.profession.ProfessionRegistry;
import com.lion.datadrivenvillagers.structure.StructureDefinition;
import com.lion.datadrivenvillagers.structure.StructureRegistry;
import com.lion.datadrivenvillagers.type.TypeDefinition;
import com.lion.datadrivenvillagers.type.TypeRegistry;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.SharedConstants;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/// `/ddv doctor`: every `/ddv why` at once, as a text file to paste into an issue.
///
/// Chat is the wrong place for a hundred lines. This writes versions, the folder, every loaded
/// definition's report and every rejected file's reason into one file, and reports in chat only how
/// many broke and where the file is.
///
/// The file covers the config folder only; a villager standing somewhere is `/ddv why villager`'s business.
public final class DoctorCommand {

    private static final String FILE = "doctor.txt";

    private DoctorCommand() {
    }

    public static LiteralArgumentBuilder<ServerCommandSource> node() {
        return CommandManager.literal("doctor").executes(Framed.framed(DoctorCommand::doctor));
    }

    private static int doctor(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        List<String> lines = new ArrayList<>();
        int broken = 0;
        int reports = 0;

        lines.add("DataDrivenVillagers doctor, " + ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        lines.add("Minecraft " + SharedConstants.getGameVersion().getName() + ", " + PlatformInfo.loader()
                + ", DataDrivenVillagers " + PlatformInfo.modVersion());
        lines.add("Config folder: " + ProfessionLoader.directory().getParent());
        lines.add("Loaded: " + ProfessionRegistry.ordered().size() + " profession(s), "
                + TypeRegistry.ordered().size() + " type(s), " + StructureRegistry.ordered().size() + " structure(s)");
        lines.add("Rejected: " + ProfessionRegistry.errors().size() + " profession file(s), "
                + TypeRegistry.errors().size() + " type file(s), " + StructureRegistry.errors().size() + " structure file(s)");
        clientsNeed(lines);
        lines.add("");

        lines.add("== Rejected files ==");
        if (ProfessionRegistry.errors().isEmpty() && TypeRegistry.errors().isEmpty()
                && StructureRegistry.errors().isEmpty()) {
            lines.add("none");
        }
        ProfessionRegistry.errors().forEach(error -> lines.add("professions/" + error.file() + "  " + error.reason()));
        TypeRegistry.errors().forEach(error -> lines.add("types/" + error.file() + "  " + error.reason()));
        StructureRegistry.errors().forEach(error -> lines.add("structures/" + error.file() + "  " + error.reason()));
        lines.add("");

        for (ProfessionDefinition definition : ProfessionRegistry.ordered()) {
            Report report = WhyCommand.professionReport(source, definition);
            broken += append(lines, report);
            reports++;
        }
        for (TypeDefinition definition : TypeRegistry.ordered()) {
            Report report = WhyCommand.typeReport(source, definition);
            broken += append(lines, report);
            reports++;
        }
        for (StructureDefinition definition : StructureRegistry.ordered()) {
            Report report = WhyCommand.structureReport(source, definition);
            broken += append(lines, report);
            reports++;
        }

        Path file = ProfessionLoader.directory().getParent().resolve(FILE);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            source.sendError(Text.literal("Could not write " + file + ": " + e.getMessage()));
            return 0;
        }

        int rejected = ProfessionRegistry.errors().size() + TypeRegistry.errors().size()
                + StructureRegistry.errors().size();
        int finalBroken = broken;
        int finalReports = reports;
        source.sendFeedback(() -> Text.literal(finalReports + " report(s), " + finalBroken + " broken, "
                        + rejected + " file(s) rejected")
                .formatted(finalBroken + rejected == 0 ? Formatting.GREEN : Formatting.YELLOW), false);
        source.sendFeedback(() -> Text.literal("Wrote ").formatted(Formatting.GREEN)
                .append(Text.literal(file.toString()).formatted(Formatting.YELLOW))
                .append(Text.literal("  paste it into an issue as it is.").formatted(Formatting.GRAY)), false);
        return reports;
    }

    /// The files every client has to bring, named as such.
    private static void clientsNeed(List<String> lines) {
        List<String> files = new ArrayList<>();
        ProfessionRegistry.ordered().stream().filter(definition -> !definition.isOverride())
                .forEach(definition -> files.add("professions/" + definition.name() + ".json"));
        TypeRegistry.ordered().forEach(definition -> files.add("types/" + definition.name() + ".json"));
        if (files.isEmpty()) {
            return;
        }
        lines.add("Every client needs: " + String.join(", ", files) + " - a profession or type is a "
                + "registry entry, and the game refuses a client without it (\"server sent registries "
                + "with unknown keys\"). /ddv export <name> builds the zip to hand out.");
    }

    private static int append(List<String> lines, Report report) {
        lines.addAll(report.plain());
        lines.add("");
        return report.isBroken() ? 1 : 0;
    }
}
