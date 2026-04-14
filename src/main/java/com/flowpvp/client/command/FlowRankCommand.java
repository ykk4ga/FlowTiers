package com.flowpvp.client.command;

import com.flowpvp.client.FlowPvPClient;
import com.flowpvp.client.config.ModConfig;
import com.flowpvp.client.data.PlayerStats;
import com.flowpvp.client.data.RankedLadder;
import com.flowpvp.client.data.TierInfo;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * /flowrank               — shows own stats for the currently configured display mode
 * /flowrank <player>      — shows another player's stats
 *
 * Always shows the full ladder breakdown regardless of the configured display mode.
 */
public final class FlowRankCommand {

    /** Ordered list of all ladder keys as returned by the API. */
    private static final String[] LADDER_ORDER = {
            "SWORD", "AXE", "UHC", "VANILLA", "MACE",
            "DIAMOND_POT", "NETHERITE_OP", "SMP", "DIAMOND_SMP"
    };

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(
                        ClientCommandManager.literal("flowrank")
                                .executes(ctx -> lookupSelf(ctx))
                                .then(ClientCommandManager.argument("player", StringArgumentType.word())
                                        .executes(ctx -> lookupPlayer(
                                                ctx, StringArgumentType.getString(ctx, "player"))))
                )
        );
    }

    // -------------------------------------------------------------------------

    private static int lookupSelf(CommandContext<FabricClientCommandSource> ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            ctx.getSource().sendFeedback(error("Not in-game."));
            return 0;
        }
        ctx.getSource().sendFeedback(info("Looking up your stats..."));
        FlowPvPClient.STATS_CACHE.getStatsByUuid(mc.player.getUuid())
                .thenAcceptAsync(stats ->
                        mc.execute(() -> printStats(ctx, stats))
                )
                .exceptionally(ex -> {
                    mc.execute(() -> ctx.getSource().sendFeedback(
                            error("Failed: " + rootMessage(ex))));
                    return null;
                });
        return 1;
    }

    private static int lookupPlayer(CommandContext<FabricClientCommandSource> ctx, String username) {
        ctx.getSource().sendFeedback(info("Looking up " + username + "..."));
        MinecraftClient mc = MinecraftClient.getInstance();
        FlowPvPClient.STATS_CACHE.getStatsByUsername(username)
                .thenAcceptAsync(stats ->
                        mc.execute(() -> printStats(ctx, stats))
                )
                .exceptionally(ex -> {
                    mc.execute(() -> ctx.getSource().sendFeedback(
                            error("Failed to fetch stats for " + username + ": " + rootMessage(ex))));
                    return null;
                });
        return 1;
    }

    // -------------------------------------------------------------------------
    // Output builders
    // -------------------------------------------------------------------------

    private static void printStats(CommandContext<FabricClientCommandSource> ctx, PlayerStats stats) {
        ctx.getSource().sendFeedback(buildHeader(stats));
        ctx.getSource().sendFeedback(buildLadderBreakdown(stats));
    }

    /** Line 1: [FlowTiers] ZursToes — Iron III | 800 ELO | #15,041 globally
     *  Always shows global stats regardless of the configured display mode. */
    private static Text buildHeader(PlayerStats stats) {
        TierInfo tier = stats.getDisplayTier(RankedLadder.GLOBAL);
        int elo  = stats.getDisplayElo(RankedLadder.GLOBAL);
        int pos  = stats.getDisplayPosition(RankedLadder.GLOBAL);

        NumberFormat fmt = NumberFormat.getNumberInstance(Locale.US);

        MutableText msg = Text.literal("[FlowTiers] ").formatted(Formatting.AQUA)
                .append(Text.literal(stats.lastKnownName).formatted(Formatting.WHITE))
                .append(Text.literal(" — ").formatted(Formatting.GRAY))
                .append(Text.literal(tier.displayName).setStyle(Style.EMPTY.withColor(tier.rgb())))
                .append(Text.literal(" | " + elo + " ELO").formatted(Formatting.WHITE));

        if (pos > 0) {
            msg.append(Text.literal(" | #" + fmt.format(pos) + " globally")
                    .formatted(Formatting.YELLOW));
        }

        return msg;
    }

    /** Line 2: compact per-ladder summary */
    private static Text buildLadderBreakdown(PlayerStats stats) {
        NumberFormat fmt = NumberFormat.getNumberInstance(Locale.US);
        MutableText out = Text.empty();
        boolean first = true;

        for (RankedLadder key : RankedLadder.values()) {
            if (key == RankedLadder.GLOBAL || key == RankedLadder.HIGHEST_TIER) continue;
            PlayerStats.LadderStats ls = stats.perLadder.get(key);
            if (ls == null || ls.isUnranked()) continue;

            String label = ModConfig.displayModeLabel(key);

            if (!first) out.append(Text.literal("\n"));
            first = false;

            MutableText entry = Text.literal("  "); // indent

            if (ls.isInPlacements()) {
                entry.append(Text.literal(label + ": ").formatted(Formatting.GRAY))
                        .append(Text.literal("Placements " + ls.placementMatchesPlayed)
                                .setStyle(Style.EMPTY.withColor(ls.tier.rgb())));
            } else {
                entry.append(Text.literal(label + ": ").formatted(Formatting.GRAY))
                        .append(Text.literal(ls.tier.displayName)
                                .setStyle(Style.EMPTY.withColor(ls.tier.rgb())))
                        .append(Text.literal(" " + ls.totalRating + " ELO").formatted(Formatting.WHITE));

                if (ls.position > 0) {
                    entry.append(Text.literal(" #" + fmt.format(ls.position))
                            .formatted(Formatting.YELLOW));
                }

                if (ls.totalGames() > 0) {
                    entry.append(Text.literal(
                                    " (" + ls.wins + "W/" + ls.losses + "L"
                                            + " " + String.format("%.0f%%", ls.winRate()) + ")")
                            .formatted(Formatting.GRAY));
                }
            }

            out.append(entry);
        }

        if (first) {
            out.append(Text.literal("No ranked games yet.").formatted(Formatting.DARK_GRAY));
        }

        return out;
    }

    // -------------------------------------------------------------------------

    private static Text info(String msg) {
        return Text.literal("[FlowTiers] " + msg).formatted(Formatting.AQUA);
    }

    private static Text error(String msg) {
        return Text.literal("[FlowTiers] " + msg).formatted(Formatting.RED);
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }
}
