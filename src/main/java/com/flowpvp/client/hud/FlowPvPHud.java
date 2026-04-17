package com.flowpvp.client.hud;

import com.flowpvp.client.FlowPvPClient;
import com.flowpvp.client.config.ModConfig;
import com.flowpvp.client.data.PlayerStats;
import com.flowpvp.client.data.RankedLadder;
import com.flowpvp.client.data.TierInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Renders the FlowTiers rank HUD overlay.
 *
 * Respects ModConfig.displayMode to show global or per-ladder stats.
 * Component visibility is controlled by individual toggles in ModConfig.
 */
public final class FlowPvPHud {

    private static final int PADDING     = 3;
    private static final int LINE_HEIGHT = 10;

    private static final int BG_COLOR    = 0x88000000;
    private static final int FLOW_COLOR  = 0xFF00BFFF;
    private static final int WHITE       = 0xFFFFFFFF;
    private static final int GOLD        = 0xFFFFD700;
    private static final int GRAY        = 0xFFAAAAAA;
    private static final int GREEN       = 0xFF55FF55;
    private static final int RED         = 0xFFFF5555;

    private volatile PlayerStats cachedStats  = null;
    private volatile CompletableFuture<PlayerStats> pendingFetch = null;
    private volatile boolean fetchFailed = false;
    private UUID lastUuid = null;

    public void render(DrawContext ctx, RenderTickCounter tickCounter) {
        if (!ModConfig.INSTANCE.hudEnabled) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (mc.getDebugHud().shouldShowDebugHud()) return;

        UUID uuid = mc.player.getUuid();
        long ttl  = ModConfig.INSTANCE.cacheMinutes * 60_000L;

        // Re-trigger fetch if UUID changed or cache went stale
        if (!uuid.equals(lastUuid) || (cachedStats != null && cachedStats.isStale(ttl))) {
            lastUuid = uuid;
            cachedStats = null;
            fetchFailed = false;
            triggerFetch(uuid);
        } else if (cachedStats == null && pendingFetch == null && !fetchFailed) {
            triggerFetch(uuid);
        }

        TextRenderer tr = mc.textRenderer;
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();
        int w = tr.getWidth("FlowTiers") + PADDING * 2;
        int x = Math.max(0, Math.min(ModConfig.INSTANCE.hudX, screenW - w));
        int y = Math.max(0, Math.min(ModConfig.INSTANCE.hudY, screenH - LINE_HEIGHT));

        if (cachedStats == null) {
            String text = fetchFailed ? "FlowTiers: fetch failed" : "FlowTiers: loading...";
            ctx.fill(x, y, x + w, y + LINE_HEIGHT + PADDING * 2, BG_COLOR);
            ctx.drawText(tr, text, x + PADDING, y + PADDING, GRAY, true);
            return;
        }

        drawStats(ctx, tr, x, y);
    }

    private void drawStats(DrawContext ctx, TextRenderer tr, int x, int y) {
        ModConfig cfg     = ModConfig.INSTANCE;
        PlayerStats stats = cachedStats;
        RankedLadder mode = cfg.displayMode;
        NumberFormat fmt  = NumberFormat.getNumberInstance(Locale.US);

        TierInfo tier = stats.getDisplayTier(mode);
        int elo       = stats.getDisplayElo(mode);
        int pos       = stats.getDisplayPosition(mode);
        int wins      = stats.getDisplayWins(mode);
        int losses    = stats.getDisplayLosses(mode);
        int streak    = stats.getDisplayStreak(mode);

        boolean perLadder = mode != RankedLadder.GLOBAL;

        // ---- Compute header line ----
        String headerLine;
        if (mode == RankedLadder.GLOBAL) {
            headerLine = "FlowTiers";
        } else if (mode == RankedLadder.HIGHEST_TIER) {
            // Show which ladder was auto-selected as the best
            RankedLadder best = stats.getHighestTierLadder();
            headerLine = "FlowTiers  [" + ModConfig.displayModeLabel(best) + " \u2605]";
        } else {
            headerLine = "FlowTiers  [" + ModConfig.displayModeLabel(mode) + "]";
        }

        // ---- Compute optional lines ----
        String tierEloLine = buildTierEloLine(cfg, tier, elo);

        String posLine = (cfg.hudShowPosition && pos > 0)
                ? "#" + fmt.format(pos) + (perLadder ? " ranked" : " globally")
                : null;

        String wlLine = (cfg.hudShowWinLoss && perLadder)
                ? wins + "W  " + losses + "L"
                + (wins + losses > 0
                ? "  (" + String.format("%.0f%%", (double) wins / (wins + losses) * 100) + ")"
                : "")
                : null;

        String streakLine = (cfg.hudShowStreak && perLadder)
                ? buildStreakLine(streak)
                : null;

        // ---- Measure background ----
        int maxW = tr.getWidth(headerLine);
        if (tierEloLine != null) maxW = Math.max(maxW, tr.getWidth(tierEloLine));
        if (posLine    != null) maxW = Math.max(maxW, tr.getWidth(posLine));
        if (wlLine     != null) maxW = Math.max(maxW, tr.getWidth(wlLine));
        if (streakLine != null) maxW = Math.max(maxW, tr.getWidth(streakLine));

        int lines = 1 // header
                + (tierEloLine != null ? 1 : 0)
                + (posLine     != null ? 1 : 0)
                + (wlLine      != null ? 1 : 0)
                + (streakLine  != null ? 1 : 0);

        ctx.fill(x, y, x + maxW + PADDING * 2, y + lines * LINE_HEIGHT + PADDING * 2, BG_COLOR);

        int tx = x + PADDING;
        int ty = y + PADDING;

        // Header
        ctx.drawText(tr, headerLine, tx, ty, FLOW_COLOR, true);
        ty += LINE_HEIGHT;

        // Tier + ELO (rendered in two colors on the same line)
        if (cfg.hudShowTierName || cfg.hudShowElo) {
            int cx = tx;
            if (cfg.hudShowTierName) {
                ctx.drawText(tr, tier.displayName, cx, ty, tier.packedColor, true);
                cx += tr.getWidth(tier.displayName);
            }
            if (cfg.hudShowElo) {
                ctx.drawText(tr, "  " + elo + " ELO", cx, ty, WHITE, true);
            }
            ty += LINE_HEIGHT;
        }

        if (posLine != null) {
            ctx.drawText(tr, posLine, tx, ty, GOLD, true);
            ty += LINE_HEIGHT;
        }

        if (wlLine != null) {
            ctx.drawText(tr, wlLine, tx, ty, GRAY, true);
            ty += LINE_HEIGHT;
        }

        if (streakLine != null) {
            int streakColor = streak > 0 ? GREEN : streak < 0 ? RED : GRAY;
            ctx.drawText(tr, streakLine, tx, ty, streakColor, true);
        }
    }

    private static String buildTierEloLine(ModConfig cfg, TierInfo tier, int elo) {
        if (!cfg.hudShowTierName && !cfg.hudShowElo) return null;
        StringBuilder sb = new StringBuilder();
        if (cfg.hudShowTierName) sb.append(tier.displayName);
        if (cfg.hudShowElo) sb.append("  ").append(elo).append(" ELO");
        return sb.toString();
    }

    private static String buildStreakLine(int streak) {
        if (streak > 0) return "+" + streak + " win streak";
        if (streak < 0) return streak + " loss streak";
        return "No streak";
    }

    private void triggerFetch(UUID uuid) {
        if (pendingFetch != null && !pendingFetch.isDone()) return;

        String override = ModConfig.INSTANCE.usernameOverride;
        CompletableFuture<PlayerStats> future = (override != null && !override.isBlank())
                ? FlowPvPClient.STATS_CACHE.getStatsByUsername(override)
                : FlowPvPClient.STATS_CACHE.getStatsByUuid(uuid);

        pendingFetch = future;
        future.thenAccept(stats -> {
            this.cachedStats = stats;
            this.pendingFetch = null;
        }).exceptionally(ex -> {
            this.fetchFailed = true;
            this.pendingFetch = null;
            return null;
        });
    }

    // Exposed for HudConfigScreen preview sizing
    public int getWidgetWidth(MinecraftClient mc) {
        TextRenderer tr = mc.textRenderer;
        if (cachedStats == null) return 130;
        RankedLadder mode = ModConfig.INSTANCE.displayMode;
        String tierElo = cachedStats.getDisplayTier(mode).displayName
                + "  " + cachedStats.getDisplayElo(mode) + " ELO";
        return Math.max(tr.getWidth("FlowTiers"), tr.getWidth(tierElo)) + PADDING * 2;
    }

    public int getWidgetHeight() {
        ModConfig cfg = ModConfig.INSTANCE;
        boolean perLadder = !RankedLadder.GLOBAL.equals(cfg.displayMode);
        int lines = 1; // header
        if (cfg.hudShowTierName || cfg.hudShowElo) lines++;
        if (cfg.hudShowPosition) lines++;
        if (cfg.hudShowWinLoss && perLadder) lines++;
        if (cfg.hudShowStreak && perLadder) lines++;
        return lines * LINE_HEIGHT + PADDING * 2;
    }
}
