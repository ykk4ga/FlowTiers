package com.flowpvp.client.screen;

import com.flowpvp.client.FlowPvPClient;
import com.flowpvp.client.config.ModConfig;
import com.flowpvp.client.data.PlayerStats;
import com.flowpvp.client.data.RankedLadder;
import com.flowpvp.client.data.TierInfo;
import com.flowpvp.client.util.GamemodeIcons;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Detail screen showing every-ladder breakdown for a single player. Reached
 * by clicking a row in {@link LeaderboardScreen}. Fetches the player's stats
 * via the existing STATS_CACHE and renders a tier/ELO/position/W-L/streak
 * table per ladder, plus a global summary line.
 */
public class PlayerStatsScreen extends Screen {

    private static final int BG_COLOR     = 0xE6101010;
    private static final int TITLE_COLOR  = 0xFF00BFFF;
    private static final int HEADER_COLOR = 0xFF888888;
    private static final int ROW_ALT_BG   = 0x22FFFFFF;
    private static final int GOLD         = 0xFFFFD700;
    private static final int GREEN        = 0xFF55FF55;
    private static final int RED          = 0xFFFF5555;
    private static final int GRAY         = 0xFFAAAAAA;
    private static final int WHITE        = 0xFFFFFFFF;

    /** Per-ladder rows (excludes GLOBAL & HIGHEST_TIER which are summaries). */
    private static final RankedLadder[] LADDERS = {
            RankedLadder.SWORD,
            RankedLadder.AXE,
            RankedLadder.UHC,
            RankedLadder.VANILLA,
            RankedLadder.MACE,
            RankedLadder.DIAMOND_POT,
            RankedLadder.NETHERITE_OP,
            RankedLadder.SMP,
            RankedLadder.DIAMOND_SMP
    };

    private final Screen parent;
    private final String username;

    private volatile PlayerStats stats;
    private volatile boolean fetchFailed;
    private CompletableFuture<PlayerStats> pendingFetch;

    public PlayerStatsScreen(Screen parent, String username) {
        super(Text.literal("Player Stats \u2014 " + username));
        this.parent = parent;
        this.username = username;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("\u2190 Back"), btn -> {
            if (client != null) client.setScreen(parent);
        }).dimensions(width / 2 - 50, height - 28, 100, 20).build());

        triggerFetch();
    }

    private void triggerFetch() {
        if (pendingFetch != null) return;
        try {
            pendingFetch = FlowPvPClient.STATS_CACHE.getStatsByUsername(username);
            pendingFetch.thenAccept(s -> {
                this.stats = s;
            }).exceptionally(ex -> {
                this.fetchFailed = true;
                return null;
            });
        } catch (Exception ex) {
            this.fetchFailed = true;
        }
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, BG_COLOR);
    }

    @Override
    public void renderInGameBackground(DrawContext ctx) {
        ctx.fill(0, 0, width, height, BG_COLOR);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Title
        ctx.drawCenteredTextWithShadow(textRenderer, username, width / 2, 12, TITLE_COLOR);
        ctx.drawCenteredTextWithShadow(textRenderer,
                "FlowPvP player stats", width / 2, 26, HEADER_COLOR);

        if (stats == null) {
            String msg = fetchFailed ? "Failed to load stats" : "Loading...";
            ctx.drawCenteredTextWithShadow(textRenderer, msg, width / 2, height / 2, GRAY);
            super.render(ctx, mouseX, mouseY, delta);
            return;
        }

        NumberFormat fmt = NumberFormat.getNumberInstance(Locale.US);

        // Layout — table is centered, columns fixed width
        int tableW = 460;
        int x0 = (width - tableW) / 2;
        int colLadder = x0;
        int colTier   = x0 + 110;
        int colElo    = x0 + 220;
        int colPos    = x0 + 290;
        int colWL     = x0 + 350;
        int colStreak = x0 + 420;

        int startY = 50;
        int lineH  = 14;

        // Header row
        ctx.drawTextWithShadow(textRenderer, "Ladder", colLadder, startY, HEADER_COLOR);
        ctx.drawTextWithShadow(textRenderer, "Tier",   colTier,   startY, HEADER_COLOR);
        ctx.drawTextWithShadow(textRenderer, "ELO",    colElo,    startY, HEADER_COLOR);
        ctx.drawTextWithShadow(textRenderer, "Pos",    colPos,    startY, HEADER_COLOR);
        ctx.drawTextWithShadow(textRenderer, "W/L",    colWL,     startY, HEADER_COLOR);
        ctx.drawTextWithShadow(textRenderer, "Streak", colStreak, startY, HEADER_COLOR);
        ctx.fill(x0 - 4, startY + 11, x0 + tableW, startY + 12, 0xFF444444);

        int y = startY + 16;
        for (int i = 0; i < LADDERS.length; i++) {
            RankedLadder ladder = LADDERS[i];

            if (i % 2 == 0) {
                ctx.fill(x0 - 4, y - 2, x0 + tableW, y + lineH - 4, ROW_ALT_BG);
            }

            // Ladder name with icon prefix
            String iconChar = GamemodeIcons.getIconChar(ladder);
            String ladderLabel = (iconChar != null ? iconChar + "  " : "")
                    + ModConfig.displayModeLabel(ladder);
            ctx.drawTextWithShadow(textRenderer, ladderLabel, colLadder, y, WHITE);

            // Tier — colored by tier
            TierInfo tier = stats.getDisplayTier(ladder);
            ctx.drawTextWithShadow(textRenderer,
                    tier.displayName, colTier, y, tier.packedColor);

            // ELO — colored by band
            int elo = stats.getDisplayElo(ladder);
            ctx.drawTextWithShadow(textRenderer,
                    fmt.format(elo), colElo, y, eloColor(elo));

            // Position
            int pos = stats.getDisplayPosition(ladder);
            String posStr = pos > 0 ? "#" + fmt.format(pos) : "\u2014";
            ctx.drawTextWithShadow(textRenderer, posStr, colPos, y,
                    pos > 0 ? GOLD : GRAY);

            // Wins / Losses
            int wins   = stats.getDisplayWins(ladder);
            int losses = stats.getDisplayLosses(ladder);
            int total  = wins + losses;
            String wlStr;
            int wlColor;
            if (total > 0) {
                wlStr = wins + "/" + losses;
                double winRate = (double) wins / total;
                wlColor = winRate >= 0.55 ? GREEN
                        : winRate >= 0.45 ? GRAY
                        : RED;
            } else {
                wlStr = "\u2014";
                wlColor = GRAY;
            }
            ctx.drawTextWithShadow(textRenderer, wlStr, colWL, y, wlColor);

            // Streak
            int streak = stats.getDisplayStreak(ladder);
            String streakStr;
            int streakColor;
            if (streak > 0) {
                streakStr = "+" + streak;
                streakColor = GREEN;
            } else if (streak < 0) {
                streakStr = String.valueOf(streak);
                streakColor = RED;
            } else {
                streakStr = "\u2014";
                streakColor = GRAY;
            }
            ctx.drawTextWithShadow(textRenderer, streakStr, colStreak, y, streakColor);

            y += lineH;
        }

        // Global summary line below the table
        y += 10;
        ctx.fill(x0 - 4, y - 2, x0 + tableW, y - 1, 0xFF444444);
        y += 4;

        TierInfo gTier = stats.getDisplayTier(RankedLadder.GLOBAL);
        int gElo = stats.getDisplayElo(RankedLadder.GLOBAL);
        int gPos = stats.getDisplayPosition(RankedLadder.GLOBAL);
        String overallIcon = GamemodeIcons.getOverallIconChar();

        StringBuilder sb = new StringBuilder();
        if (overallIcon != null) sb.append(overallIcon).append("  ");
        sb.append("Global: ")
                .append(gTier.displayName)
                .append("  ")
                .append(fmt.format(gElo))
                .append(" ELO");
        if (gPos > 0) sb.append("    #").append(fmt.format(gPos)).append(" globally");

        ctx.drawTextWithShadow(textRenderer, sb.toString(), x0, y, TITLE_COLOR);

        // Highest-tier callout
        try {
            RankedLadder best = stats.getHighestTierLadder();
            if (best != null) {
                String bestIcon = GamemodeIcons.getIconChar(best);
                TierInfo bestTier = stats.getDisplayTier(best);
                String line = (bestIcon != null ? bestIcon + "  " : "")
                        + "Highest Tier: " + bestTier.displayName
                        + " (" + ModConfig.displayModeLabel(best) + ")";
                ctx.drawTextWithShadow(textRenderer, line, x0, y + 12, GOLD);
            }
        } catch (Exception ignored) {
            // best-effort — if PlayerStats hasn't resolved a "highest" yet, just skip the line
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    /** Same band colors as the leaderboard for consistency. */
    private static int eloColor(int elo) {
        if (elo >= 2000) return 0xFFFF55FF;
        if (elo >= 1700) return 0xFF55FFFF;
        if (elo >= 1400) return 0xFFFFAA00;
        if (elo >= 1100) return 0xFFFFD700;
        if (elo >= 800)  return 0xFFC0C0C0;
        if (elo >= 500)  return 0xFFCD7F32;
        return 0xFFAAAAAA;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}