package com.lion.datadrivenvillagers.command;

import com.lion.datadrivenvillagers.platform.JobSiteStates;
import com.lion.datadrivenvillagers.profession.ProfessionLoader;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.command.CommandSource;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.poi.PointOfInterestType;
import net.minecraft.world.poi.PointOfInterestTypes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/// `/ddv blocks [text]`: which blocks are already bound to a point of interest. A block state belongs
/// to exactly one POI, so the taken blocks (a few dozen) are listed and everything else is free.
public final class BlocksCommand {

    private static final int MAX_LINES = 30;

    /// Beds alone are sixteen blocks on one point of interest.
    private static final int MAX_BLOCKS_PER_POI = 6;

    private static final SuggestionProvider<ServerCommandSource> BLOCK_IDS = (context, builder) -> CommandSource.suggestIdentifiers(Registries.BLOCK.getIds(), builder);

    private BlocksCommand() {}

    public static LiteralArgumentBuilder<ServerCommandSource> node() {
        return CommandManager.literal("blocks")
                .executes(Framed.framed(BlocksCommand::taken))
                .then(CommandManager.argument("filter", StringArgumentType.greedyString())
                        .suggests(BLOCK_IDS)
                        .executes(Framed.framed(BlocksCommand::search)));
    }

    /// Every taken block grouped by the point of interest that holds it, vanilla, ours or another mod's.
    private static int taken(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        Map<String, Set<String>> byPoi = takenByPoi();

        int blocks = byPoi.values().stream().mapToInt(Set::size).sum();
        source.sendFeedback(() -> Text.literal(blocks + " block(s) already belong to "
                        + byPoi.size() + " point(s) of interest. Any block not listed here is free.")
                .formatted(Formatting.WHITE), false);

        for (Map.Entry<String, Set<String>> entry : byPoi.entrySet()) {
            source.sendFeedback(() -> Text.literal(entry.getKey()).formatted(Formatting.RED)
                    .append(Text.literal("  " + shorten(entry.getValue())).formatted(Formatting.GRAY)), false);
        }

        source.sendFeedback(() -> Text.literal("/ddv blocks <text> checks a single block, or every "
                + "block whose id contains that text.").formatted(Formatting.DARK_GRAY), false);
        return blocks;
    }

    private static Map<String, Set<String>> takenByPoi() {
        Map<String, Set<String>> byPoi = new TreeMap<>();
        for (Map.Entry<BlockState, RegistryEntry<PointOfInterestType>> entry
                : JobSiteStates.all().entrySet()) {
            String poi = entry.getValue().getKey()
                    .map(key -> key.getValue().toString())
                    .orElse("an unnamed point of interest");
            Identifier block = Registries.BLOCK.getId(entry.getKey().getBlock());
            byPoi.computeIfAbsent(poi, key -> new TreeSet<>()).add(block.toString());
        }
        return byPoi;
    }

    private static String shorten(Set<String> blocks) {
        if (blocks.size() <= MAX_BLOCKS_PER_POI) {
            return String.join(", ", blocks);
        }
        List<String> shown = new ArrayList<>(blocks).subList(0, MAX_BLOCKS_PER_POI);
        return String.join(", ", shown) + " and " + (blocks.size() - MAX_BLOCKS_PER_POI) + " more";
    }

    /// Free or taken for every block whose id contains the text.
    private static int search(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String filter = StringArgumentType.getString(context, "filter").trim().toLowerCase(Locale.ROOT);

        List<Identifier> matches = Registries.BLOCK.getIds().stream()
                .filter(id -> id.toString().contains(filter))
                .sorted(Comparator.comparing(Identifier::toString))
                .toList();

        if (matches.isEmpty()) {
            source.sendError(Text.literal("No block id contains \"" + filter + "\"."));
            return 0;
        }

        int free = 0;
        for (Identifier id : matches.subList(0, Math.min(matches.size(), MAX_LINES))) {
            Optional<String> owner = ownerOf(id);
            if (owner.isEmpty()) {
                free++;
            }
            source.sendFeedback(() -> Text.literal(id.toString())
                    .formatted(owner.isEmpty() ? Formatting.GREEN : Formatting.RED)
                    .append(Text.literal(owner.map(who -> "  taken by " + who).orElse("  free"))
                            .formatted(Formatting.GRAY)), false);
        }

        if (matches.size() > MAX_LINES) {
            source.sendFeedback(() -> Text.literal("and " + (matches.size() - MAX_LINES)
                    + " more, narrow the text down").formatted(Formatting.DARK_GRAY), false);
        }
        if (matches.size() == 1 && free == 1) {
            source.sendFeedback(() -> Text.literal("\"workstation\": \"" + matches.get(0) + "\"")
                    .formatted(Formatting.YELLOW), false);
        }
        return free;
    }

    /// Uses the loader's rule that one taken state makes a block unusable, so this never recommends a rejected block.
    private static Optional<String> ownerOf(Identifier id) {
        Optional<Block> block = Registries.BLOCK.getOrEmpty(id);
        if (block.isEmpty()) {
            return Optional.of("no such block");
        }
        return ProfessionLoader.existingOwner(PointOfInterestTypes.getStatesOfBlock(block.get()));
    }
}
