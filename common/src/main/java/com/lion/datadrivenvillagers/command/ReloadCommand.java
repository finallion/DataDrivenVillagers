package com.lion.datadrivenvillagers.command;

import com.lion.datadrivenvillagers.ReloadOutcome;
import com.lion.datadrivenvillagers.network.LookSync;
import com.lion.datadrivenvillagers.profession.ProfessionLoader;
import com.lion.datadrivenvillagers.structure.StructureLoader;
import com.lion.datadrivenvillagers.type.TypeLoader;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/// `/ddv reload`: reads the config folder again without a restart. Professions and villager types
/// live in registries that freeze before any world exists, so added, removed or renumbered ones are
/// reported as needing a restart rather than silently skipped.
public final class ReloadCommand {

    private ReloadCommand() {
    }

    public static LiteralArgumentBuilder<ServerCommandSource> node() {
        return CommandManager.literal("reload").executes(Framed.framed(ReloadCommand::reload));
    }

    private static int reload(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();

        List<ReloadOutcome> outcomes = new ArrayList<>(ProfessionLoader.reload(source.getServer()));
        outcomes.addAll(TypeLoader.reload(source.getServer().getRegistryManager()
                .getOrThrow(RegistryKeys.BIOME)));
        // Structures reload whole: nbt is read on demand, pools are rebuilt per world.
        outcomes.addAll(StructureLoader.load(source.getServer()));

        if (outcomes.isEmpty()) {
            source.sendFeedback(() -> Text.literal("Nothing to read, the config folder is empty.")
                    .formatted(Formatting.GRAY), false);
            return 0;
        }

        // After all loaders. In single player this is how the integrated server's new png reaches
        // the renderer.
        int players = LookSync.broadcast(source.getServer());
        outcomes.add(ReloadOutcome.updated("(looks)", "textures and hats sent to " + players + " player(s)"));

        for (ReloadOutcome outcome : outcomes) {
            source.sendFeedback(() -> line(outcome), false);
        }

        int applied = count(outcomes, ReloadOutcome.Kind.UPDATED);
        int restart = count(outcomes, ReloadOutcome.Kind.RESTART);
        int rejected = count(outcomes, ReloadOutcome.Kind.REJECTED);

        source.sendFeedback(() -> Text.literal(applied + " applied, "
                        + count(outcomes, ReloadOutcome.Kind.UNCHANGED) + " unchanged, "
                        + restart + " waiting for a restart, " + rejected + " rejected")
                .formatted(restart + rejected == 0 ? Formatting.GREEN : Formatting.WHITE), false);

        if (restart > 0) {
            source.sendFeedback(() -> Text.literal("Professions, job sites and villager types are "
                            + "registered before any world exists. Nothing short of restarting the game "
                            + "can add, remove or renumber one.")
                    .formatted(Formatting.YELLOW), false);
        }
        return applied;
    }

    private static Text line(ReloadOutcome outcome) {
        String mark = switch (outcome.kind()) {
            case UPDATED -> "[ok] ";
            case UNCHANGED -> "[--] ";
            case RESTART -> "[!!] ";
            case REJECTED -> "[no] ";
        };
        Formatting colour = switch (outcome.kind()) {
            case UPDATED -> Formatting.GREEN;
            case UNCHANGED -> Formatting.DARK_GRAY;
            case RESTART -> Formatting.YELLOW;
            case REJECTED -> Formatting.RED;
        };

        String detail = outcome.detail().isEmpty()
                ? (outcome.kind() == ReloadOutcome.Kind.UNCHANGED ? "unchanged" : "")
                : outcome.detail();
        return Text.literal(mark).formatted(colour)
                .append(Text.literal(outcome.file()).formatted(Formatting.WHITE))
                .append(Text.literal(detail.isEmpty() ? "" : "  " + detail).formatted(Formatting.GRAY));
    }

    private static int count(List<ReloadOutcome> outcomes, ReloadOutcome.Kind kind) {
        return (int) outcomes.stream().filter(outcome -> outcome.kind() == kind).count();
    }
}
