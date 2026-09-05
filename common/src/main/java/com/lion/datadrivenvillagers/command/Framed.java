package com.lion.datadrivenvillagers.command;

import com.mojang.brigadier.Command;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/// Prints the typed command as a divider above its own output.
final class Framed {

    private static final String RULE = "────────────";

    private Framed() {
    }

    static Command<ServerCommandSource> framed(Command<ServerCommandSource> command) {
        return context -> {
            String typed = "/" + context.getInput();
            context.getSource().sendFeedback(() -> Text.literal(RULE + " ")
                    .append(Text.literal(typed).formatted(Formatting.WHITE))
                    .append(Text.literal(" " + RULE))
                    .formatted(Formatting.DARK_GRAY), false);
            return command.run(context);
        };
    }
}
